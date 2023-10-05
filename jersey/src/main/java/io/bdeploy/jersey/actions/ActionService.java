package io.bdeploy.jersey.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@Singleton
public class ActionService {

    @FunctionalInterface
    public interface ActionHandle extends AutoCloseable {

        @Override
        public void close();
    }

    public static final String ACTIONS_TYPE = "SERVER_ACTIONS";
    public static final String ACTIONS_PAYLOAD = "SERVER_ACTION";

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final ObjectChangeBroadcaster bc;
    private final Auditor auditor;

    private final Map<Action, Set<ActionExecution>> running = new TreeMap<>();

    private final ScheduledExecutorService cleaner = Executors
            .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Action-Cleanup"));

    public ActionService(ObjectChangeBroadcaster bc, Auditor auditor) {
        this.bc = bc;
        this.auditor = auditor;

        cleaner.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    public ActionHandle start(Action action, ActionExecution exec) {
        internalAdd(action, exec, true);

        auditor.audit(
                new AuditRecord.Builder().setWho(exec.getName()).setMethod("Begin").setMessage(action.getType().getDescription())
                        .addParameter("type", action.getType().name()).addParameter("bhive", action.getBHive())
                        .addParameter("instance", action.getInstance()).addParameter("item", action.getItem()).build());

        return () -> stop(action, exec);
    }

    private void stop(Action action, ActionExecution exec) {
        // auditing happens on stop, which should be called for all action *except* for those that are
        // open ended by design (restart, install update, ...).
        auditor.audit(
                new AuditRecord.Builder().setWho(exec.getName()).setMethod("Done").setMessage(action.getType().getDescription())
                        .addParameter("type", action.getType().name()).addParameter("bhive", action.getBHive())
                        .addParameter("instance", action.getInstance()).addParameter("item", action.getItem())
                        .addParameter("duration", System.currentTimeMillis() - exec.getStart() + "ms").build());

        internalRemove(action, exec);
    }

    private void withExecutions(Action action, Consumer<Set<ActionExecution>> consumer) {
        synchronized (running) {
            consumer.accept(running.computeIfAbsent(action, a -> new TreeSet<>()));
        }
    }

    private void cleanup() {
        synchronized (running) {
            List<Action> notRunning = running.entrySet().stream().filter(e -> e.getValue().isEmpty()).map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            notRunning.forEach(running::remove);
        }
    }

    public List<ActionBroadcastDto> getRunningActions(String bhive, String instance) {
        List<ActionBroadcastDto> result = new ArrayList<>();

        synchronized (running) {
            for (Map.Entry<Action, Set<ActionExecution>> entry : running.entrySet()) {
                if (bhive != null && !bhive.equals(entry.getKey().getBHive())) {
                    // skip :) the bhive is not as requested. null request matches all.
                    continue;
                }

                if (instance != null && !instance.equals(entry.getKey().getInstance())) {
                    // skip :) the instance is not as requested. null request matches all.
                    continue;
                }

                for (ActionExecution exec : entry.getValue()) {
                    result.add(new ActionBroadcastDto(entry.getKey(), exec));
                }
            }
        }

        return result;
    }

    private void internalAdd(Action action, ActionExecution exec, boolean failOnConflict) {
        withExecutions(action, execs -> {
            // if exclusive, either the set is empty, or the only entry is our own execution.
            if (action.getType().isExclusive() && !execs.isEmpty() && !(execs.size() == 1 && execs.contains(exec))) {
                if (failOnConflict) {
                    throw new WebApplicationException("Operation is already running: " + action, Status.CONFLICT);
                } else {
                    // we cannot *prevent* it, we just log the fact. we can now no longer start that
                    // action ourselves without the remote action going away.
                    log.warn("Action conflict while adding {} by {}. Already running: {}", action, exec, execs);
                }
            }

            if (!execs.contains(exec)) {
                execs.add(exec);
                broadcast(action, exec, ObjectEvent.CREATED);
            }
        });
    }

    private void internalRemove(Action action, ActionExecution exec) {
        withExecutions(action, execs -> {
            if (!execs.contains(exec)) {
                log.warn("Cannot remove execution which is not existing: {} in {}", exec, execs);
            } else {
                execs.remove(exec);
                broadcast(action, exec, ObjectEvent.REMOVED);
            }
        });
    }

    protected void add(ActionBroadcastDto... actions) {
        for (ActionBroadcastDto dto : actions) {
            internalAdd(dto.action, dto.execution, false);
        }
    }

    protected void remove(ActionBroadcastDto... actions) {
        for (ActionBroadcastDto dto : actions) {
            internalRemove(dto.action, dto.execution);
        }
    }

    protected void removeSource(String source) {
        synchronized (running) {
            for (Map.Entry<Action, Set<ActionExecution>> entry : running.entrySet()) {
                List<ActionExecution> owned = entry.getValue().stream().filter(ex -> source.equals(ex.getSource()))
                        .collect(Collectors.toList());

                owned.forEach(x -> internalRemove(entry.getKey(), x));
            }
        }
    }

    private void broadcast(Action action, ActionExecution exec, ObjectEvent type) {
        if (bc == null) {
            return;
        }

        try {
            ActionBroadcastDto act = new ActionBroadcastDto(action, exec);
            List<String> scopes = new ArrayList<>();
            if (act.action.getBHive() != null) {
                scopes.add(act.action.getBHive());
                if (act.action.getInstance() != null) {
                    scopes.add(act.action.getInstance());
                    // no further scoping as the web ui maximum refined scope is instance level.
                }
            }

            ObjectScope scope = new ObjectScope(scopes);
            bc.send(new ObjectChangeDto(ACTIONS_TYPE, scope, type, Collections.singletonMap(ACTIONS_PAYLOAD, serialize(act))));

        } catch (Exception e) {
            log.warn("Cannot broadcast server action", e);
        }
    }

    private String serialize(ActionBroadcastDto dtos) {
        try {
            return JacksonHelper.getDefaultJsonObjectMapper().writeValueAsString(dtos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize server actions", e);
        }
    }
}

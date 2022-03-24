package io.bdeploy.jersey.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * An activity reporter which exposes currently running activities to be broadcasted via SSE
 */
@Service
public class JerseyBroadcastingActivityReporter implements ActivityReporter {

    /** Needs to be in line with ObjectChangeType for the Web UI */
    public static final String OCT_ACTIVIES = "ACTIVITIES";

    private static final Logger log = LoggerFactory.getLogger(JerseyBroadcastingActivityReporter.class);

    /**
     * All state must be static (VM global) as this service might be instantiated from multiple Jersey applications (plug-ins).
     * It seems that HK2 has a bug where it changes the registration for a singleton service in a locator if the service is
     * registered as singleton in ANOTHER locator...
     */
    private static final List<JerseyRemoteActivity> globalActivities = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<JerseyRemoteActivity> currentActivity = new InheritableThreadLocal<>();
    private static final Set<ObjectScope> activeScopes = new TreeSet<>();

    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    @Inject
    private JerseyScopeService jss;

    @Inject
    @Optional
    private ObjectChangeBroadcaster bc;

    @Inject
    public JerseyBroadcastingActivityReporter(@Named(JerseyServer.BROADCAST_EXECUTOR) ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(this::sendUpdate, 1, 1, TimeUnit.SECONDS);
    }

    private void sendUpdate() {
        if (bc == null) {
            return;
        }

        try {
            List<ActivitySnapshot> list = getGlobalActivities().stream().filter(Objects::nonNull)
                    .map(JerseyRemoteActivity::snapshot).collect(Collectors.toList());

            // figure out all different scopes which are there.
            List<ObjectScope> scopes = list.stream().map(a -> new ObjectScope(a.scope)).distinct().collect(Collectors.toList());

            Map<ObjectScope, List<ActivitySnapshot>> perScope = new HashMap<>();

            for (ObjectScope scope : scopes) {
                List<ActivitySnapshot> forScope = new ArrayList<>();
                for (ActivitySnapshot snapshot : list) {
                    if (snapshot.parentUuid != null) {
                        // this is a child, not a root - skip
                        continue;
                    }

                    if (scope.matches(new ObjectScope(snapshot.scope))) {
                        forScope.add(snapshot);

                        while (addChildren(forScope, list) != 0) {
                            // intentionally left blank :)
                        }
                    }
                }
                if (!forScope.isEmpty()) {
                    perScope.put(scope, forScope);
                }
            }
            activeScopes.addAll(perScope.keySet());

            List<ObjectScope> scopesToRemove = new ArrayList<>();
            for (ObjectScope active : activeScopes) {
                if (!perScope.containsKey(active)) {
                    scopesToRemove.add(active);
                }
            }

            // This will send an empty list to all the consumers that match this scope.
            // This will in some cases result in a reset of activities to empty, even though
            // this client will receive events from *another* scope. we cannot determine
            // it here, since we don't know about the actual registrations. Thus we send
            // the "update to empty" first and *right* after that the updates which may
            // contain activities for that clients as well.
            for (ObjectScope toRemove : scopesToRemove) {
                activeScopes.remove(toRemove);
                bc.send(new ObjectChangeDto(OCT_ACTIVIES, toRemove, ObjectEvent.CHANGED,
                        Collections.singletonMap(OCT_ACTIVIES, "[]")));
            }

            List<ObjectChangeDto> allMessages = new ArrayList<>();
            for (Map.Entry<ObjectScope, List<ActivitySnapshot>> e : perScope.entrySet()) {
                allMessages.add(new ObjectChangeDto(OCT_ACTIVIES, e.getKey(), ObjectEvent.CHANGED,
                        Collections.singletonMap(OCT_ACTIVIES, serialize(e.getValue()))));
            }
            bc.sendBestMatching(allMessages);
        } catch (Exception e) {
            log.error("Error while broadcasting activities", e);
        }
    }

    private String serialize(List<ActivitySnapshot> snap) {
        try {
            return serializer.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize activities", e);
        }
    }

    private int addChildren(List<ActivitySnapshot> activities, List<ActivitySnapshot> pool) {
        Set<String> haveUuids = activities.stream().map(s -> s.uuid).collect(Collectors.toCollection(TreeSet::new));

        List<ActivitySnapshot> children = new ArrayList<>();
        for (ActivitySnapshot root : activities) {
            for (ActivitySnapshot potentialChild : pool) {
                if (haveUuids.contains(potentialChild.uuid)) {
                    // have it already.
                    continue;
                }

                if (potentialChild.parentUuid != null && potentialChild.parentUuid.equals(root.uuid)) {
                    children.add(potentialChild);
                }
            }
        }
        activities.addAll(children);
        return children.size();
    }

    @Override
    public Activity start(String activity) {
        return start(activity, -1l);
    }

    @Override
    public Activity start(String activity, long maxWork) {
        return start(activity, () -> maxWork, null);
    }

    @Override
    public synchronized Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
        List<String> scope = JerseyRemoteActivityScopeServerFilter.getRequestActivityScope(jss);
        String user = jss.getUser();

        // wire activities by UUID. this is done so that serialization of activity "trees" stays
        // as flat as it is - otherwise too much traffic to clients would be produced. Clients
        // need to convert the flat list of activities to a tree representation when interested.
        JerseyRemoteActivity parent = currentActivity.get();
        String parentUuid = null;
        if (parent != null) {
            parentUuid = parent.getUuid();
        }

        JerseyRemoteActivity act = new JerseyRemoteActivity(this::done, null, activity, maxValue, currentValue, scope, user,
                System.currentTimeMillis(), UuidHelper.randomId(), parentUuid);

        if (log.isTraceEnabled()) {
            log.trace("Begin: [{}] {}", act.getUuid(), activity);
        }

        currentActivity.set(act);
        globalActivities.add(act);
        return act;
    }

    private synchronized void done(JerseyRemoteActivity act) {
        if (!globalActivities.contains(act)) {
            return; // already done.
        }

        JerseyRemoteActivity current = currentActivity.get();
        if (current != null && current.getUuid().equals(act.getUuid())) {
            // current is the one we're finishing
            if (act.getParentUuid() != null) {
                JerseyRemoteActivity parent = getActivityById(act.getParentUuid());
                if (parent != null) {
                    currentActivity.set(parent);
                } else {
                    log.warn("Parent activity no longer available: {} for {}", act.getParentUuid(), act);
                }
            } else {
                // no parent set - we are top-level.
                currentActivity.remove();
            }
        } else if (current != null) {
            // we're finishing something which is not current -> warn & ignore
            log.warn("Finished activity is not current for this thread: {}, current: {}", act, current);
        } else {
            log.warn("Finished activity but there is no current activity for this thread: {}", act);
        }

        globalActivities.remove(act);
    }

    @Override
    public NoThrowAutoCloseable proxyActivities(RemoteService service) {
        return new JerseyRemoteActivityProxy(service, this);
    }

    /**
     * @return (a copy of) all currently known activities
     */
    synchronized List<JerseyRemoteActivity> getGlobalActivities() {
        return new ArrayList<>(globalActivities);
    }

    synchronized void addProxyActivity(JerseyRemoteActivity act) {
        globalActivities.add(act);
    }

    synchronized void removeProxyActivity(JerseyRemoteActivity act) {
        globalActivities.remove(act);
    }

    /**
     * @return the current activity on the calling thread.
     */
    JerseyRemoteActivity getCurrentActivity() {
        return currentActivity.get();
    }

    void resetCurrentActivity() {
        currentActivity.remove();
    }

    JerseyRemoteActivity getActivityById(String uuid) {
        return globalActivities.stream().filter(Objects::nonNull).filter(a -> a.getUuid().equals(uuid)).findAny().orElse(null);
    }

}

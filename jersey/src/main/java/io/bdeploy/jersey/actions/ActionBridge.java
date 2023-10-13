package io.bdeploy.jersey.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.resources.ActionResource;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

/**
 * Bridges actions from managed servers to central server.
 */
@Singleton
@Service
public class ActionBridge {

    private final class ActionBridgeHandle {

        ObjectChangeClientWebSocket ws;
        RemoteService svc;
        long begin;

        public ActionBridgeHandle(ObjectChangeClientWebSocket ws, RemoteService svc, long begin) {
            this.ws = ws;
            this.svc = svc;
            this.begin = begin;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ActionBridge.class);

    private final ActionService actions;
    private final Map<String, ActionBridgeHandle> handles = new TreeMap<>();

    private final ScheduledExecutorService reaper = Executors
            .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("ActionBridge Reaper"));

    /** The same timeout as used in the web ui, see servers.service.ts -> SYNC_TIMEOUT */
    private static final long SYNC_TIMEOUT = TimeUnit.MINUTES.toMillis(15);

    public ActionBridge(ActionService svc) {
        this.actions = svc;
        reaper.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.SECONDS);
    }

    private void cleanup() {
        synchronized (handles) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, ActionBridgeHandle> entry : handles.entrySet()) {
                String name = entry.getKey();
                ActionBridgeHandle handle = entry.getValue();

                if (System.currentTimeMillis() > (handle.begin + SYNC_TIMEOUT)) {
                    // timed out. cleanup.
                    closeHandle(handles.get(name));

                    log.debug("Action bridge timed out to {}", name);

                    actions.removeSource(name);
                    toRemove.add(name);
                } else if (!handle.ws.isOpen()) {
                    // not timed out, but websocket closed! re-establish
                    closeHandle(handles.get(name));

                    log.atDebug().log("Action bridge to {} unexpectedly closed", name);

                    if (fetchCurrent(name, handle.svc)) {
                        // renew subscription.
                        handle.ws = fetchSubscription(name, handle.svc);
                    } else {
                        // cannot fetch anymore?! don't try to keep track anymore!
                        log.warn("Existing action bridge to {} cannot be renewed", name);
                        actions.removeSource(name);
                        toRemove.add(name);
                    }
                }
            }
            toRemove.forEach(handles::remove);
        }
    }

    private boolean fetchCurrent(String name, RemoteService service) {
        // initial fetch
        try {
            // get *all* actions we can get a hold of.
            List<ActionBroadcastDto> dtos = JerseyClientFactory.get(service).getProxyClient(ActionResource.class).getActions(null,
                    null);

            dtos.forEach(d -> d.execution.setSource(name));
            actions.removeSource(name); // remove existing.
            actions.add(dtos.toArray(new ActionBroadcastDto[dtos.size()]));

            log.debug("Established action bridge to {}. Initial actions: {}", name, dtos);

            return true;
        } catch (Exception e) {
            var l = log.atInfo();
            if (e instanceof NotFoundException) {
                l = log.atDebug();
            }
            // either not supported or another problem...?
            l.log("Cannot bridge actions from {}: {}", name, e);
            return false;
        }
    }

    private ObjectChangeClientWebSocket fetchSubscription(String name, RemoteService service) {
        String id = UuidHelper.randomId();
        ObjectChangeClientWebSocket ws = JerseyClientFactory.get(service).getObjectChangeWebSocket(e -> onEvent(id, name, e));
        ws.subscribe(ActionService.ACTIONS_TYPE, ObjectScope.EMPTY);
        return ws;
    }

    private void closeHandle(ActionBridgeHandle handle) {
        if (handle.ws != null) {
            handle.ws.close();
        }
    }

    public void onSync(String name, RemoteService service) {
        // if we have that already, just update the start time to begin again.
        synchronized (handles) {
            actions.removeSource(name);

            if (handles.containsKey(name)) {
                closeHandle(handles.get(name));
            }

            if (!fetchCurrent(name, service)) {
                return; // not supported or not possible.
            }

            log.debug("Sync {}", name);

            // prepare websocket and handle.
            ActionBridgeHandle handle = new ActionBridgeHandle(fetchSubscription(name, service), service,
                    System.currentTimeMillis());

            handles.put(name, handle);
        }
    }

    private void onEvent(String id, String source, ObjectChangeDto change) {
        // extract event - inverse operation from ActionService#broadcast's serialize.
        String event = change.details.get(ActionService.ACTIONS_PAYLOAD);

        if (StringHelper.isNullOrEmpty(event)) {
            return; // meh.
        }

        ActionBroadcastDto dto = deserialize(event);
        dto.execution.setSource(source);

        log.debug("{} Bridge event [{}] {} by {}", id, change.event.name(), dto.action, dto.execution);

        if (change.event == ObjectEvent.CREATED) {
            actions.add(dto);
        } else if (change.event == ObjectEvent.REMOVED) {
            actions.remove(dto);
        }
    }

    private ActionBroadcastDto deserialize(String event) {
        try {
            return JacksonHelper.getDefaultJsonObjectMapper().readValue(event, ActionBroadcastDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize server actions", e);
        }
    }

}

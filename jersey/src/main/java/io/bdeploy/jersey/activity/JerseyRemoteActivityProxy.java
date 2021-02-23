package io.bdeploy.jersey.activity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;

public class JerseyRemoteActivityProxy implements NoThrowAutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JerseyRemoteActivityProxy.class);

    private final String proxyUuid = "proxy-" + UuidHelper.randomId();

    private final JerseyBroadcastingActivityReporter reporter;
    private final RemoteService remote;
    private final JerseyRemoteActivity parent;

    private final Map<String, ActivityNode> proxiedActivities = new TreeMap<>();
    private final Map<String, String> uuidMapping = new TreeMap<>();
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);
    private final ObjectChangeClientWebSocket ws;

    public JerseyRemoteActivityProxy(RemoteService service, JerseyBroadcastingActivityReporter reporter) {
        if (service.getKeyStore() == null) {
            throw new IllegalStateException("RemoteService references a local service: " + service.getUri());
        }

        this.reporter = reporter;
        parent = reporter.getCurrentActivity();
        remote = service;

        ws = JerseyClientFactory.get(service).getObjectChangeWebSocket(this::onMessage);
        ws.subscribe(JerseyBroadcastingActivityReporter.OCT_ACTIVIES, new ObjectScope(proxyUuid));

        JerseyClientFactory.setProxyUuid(proxyUuid);
    }

    private synchronized void onMessage(ObjectChangeDto change) {
        // cannot use StorageHelper -> dependency not allowed.
        List<ActivitySnapshot> activities;
        try {
            String serialized = change.details.get(JerseyBroadcastingActivityReporter.OCT_ACTIVIES);
            activities = serializer.readValue(serialized, ActivitySnapshot.LIST_TYPE);
        } catch (IOException e) {
            log.error("Cannot read activities");
            if (log.isDebugEnabled()) {
                log.debug("Exception:", e);
            }
            return;
        }

        for (ActivitySnapshot snap : activities) {
            if (snap.scope == null || snap.scope.isEmpty() || !snap.scope.get(0).equals(proxyUuid)) {
                continue; // no scope set, cannot be interesting for us.
            }

            String mappedUuid = uuidMapping.computeIfAbsent(snap.uuid, s -> UuidHelper.randomId());
            String mappedParentUuid = snap.parentUuid == null ? null
                    : uuidMapping.computeIfAbsent(snap.parentUuid, s -> UuidHelper.randomId());

            if (proxiedActivities.containsKey(mappedUuid)) {
                updateExistingActivity(snap, mappedUuid);
            } else {
                createNewActivity(snap, mappedUuid, mappedParentUuid);
            }
        }

        // finish activities which are no longer reported.
        cleanupActivities(activities);
    }

    private void cleanupActivities(List<ActivitySnapshot> activities) {
        Set<String> reportedUuids = activities.stream().map(a -> uuidMapping.get(a.uuid)).collect(Collectors.toSet());
        Set<String> toRemove = new TreeSet<>();

        for (Map.Entry<String, ActivityNode> entry : proxiedActivities.entrySet()) {
            if (reportedUuids.contains(entry.getKey())) {
                continue;
            }

            entry.getValue().activity.done();
            toRemove.add(entry.getKey());
        }
        toRemove.forEach(proxiedActivities::remove);
    }

    private void createNewActivity(ActivitySnapshot snap, String mappedUuid, String mappedParentUuid) {
        // if an activity already exists, we must not re-create it. This happens
        // when proxying to the own server (i.e. web-backend to local master).
        String parentUuid = mappedParentUuid;
        if (parentUuid == null || !hasGlobalActivity(parentUuid)) {
            if (parent == null) {
                parentUuid = null;
            } else {
                parentUuid = parent.getUuid();
            }
        }

        // create activities which we don't have yet.
        ActivityNode node = new ActivityNode(snap, parent, this::onDone, this::onCancel, mappedUuid, parentUuid);
        proxiedActivities.put(mappedUuid, node);
        reporter.addProxyActivity(node.activity);
    }

    private void updateExistingActivity(ActivitySnapshot snap, String mappedUuid) {
        // update activities which we already have.
        ActivityNode toUpdate = proxiedActivities.get(mappedUuid);
        toUpdate.current = snap.current;
        toUpdate.max = snap.max;

        if (snap.cancel) {
            toUpdate.activity.requestCancel();
        }
    }

    private boolean hasGlobalActivity(String uuid) {
        return reporter.getActivityById(uuid) != null;
    }

    private void onDone(JerseyRemoteActivity activity) {
        reporter.removeProxyActivity(activity);
    }

    private void onCancel(JerseyRemoteActivity activity) {
        // cancel requested - delegate to remote
        String actualUuid = uuidMapping.entrySet().stream().filter(e -> e.getValue().equals(activity.getUuid())).findFirst()
                .map(Map.Entry::getKey).orElse(null);
        if (actualUuid == null) {
            return;
        }
        JerseyClientFactory.get(remote).getBaseTarget().path("/activities/" + actualUuid).request().delete();
    }

    @Override
    public void close() {
        // nested proxies not (yet?) supported.
        JerseyClientFactory.setProxyUuid(null);

        for (Map.Entry<String, ActivityNode> entry : proxiedActivities.entrySet()) {
            entry.getValue().activity.done();
        }

        if (ws != null) {
            ws.close();
        }
    }

    private static class ActivityNode {

        public JerseyRemoteActivity activity;
        public long current = 0;
        public long max = -1;

        public ActivityNode(ActivitySnapshot snapshot, JerseyRemoteActivity root, Consumer<JerseyRemoteActivity> onDone,
                Consumer<JerseyRemoteActivity> onCancel, String uuid, String parentUuid) {
            this.current = snapshot.current;
            this.max = snapshot.max;

            // remove the proxy scope, which MUST be the first element in the scope list.
            List<String> scope = snapshot.scope.subList(1, snapshot.scope.size());

            this.activity = new JerseyRemoteActivity(onDone, onCancel, snapshot.name, () -> max, () -> current, scope,
                    root == null ? snapshot.user : root.getUser(), System.currentTimeMillis() - snapshot.duration, uuid,
                    parentUuid);
        }
    }

}

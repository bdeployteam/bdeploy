package io.bdeploy.jersey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.ws.rs.sse.InboundSseEvent;

import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;

public class JerseySseActivityProxy implements NoThrowAutoCloseable {

    private final String proxyUuid = "proxy-" + UuidHelper.randomId();

    private final JerseySseActivityReporter reporter;
    private final RemoteService remote;
    private final JerseyEventSubscription subscription;
    private final JerseySseActivity parent;

    private final Map<String, ActivityNode> proxiedActivities = new TreeMap<>();
    private final Map<String, String> uuidMapping = new TreeMap<>();

    public JerseySseActivityProxy(RemoteService service, JerseySseActivityReporter reporter) {
        if (service.getKeyStore() == null) {
            throw new IllegalStateException("RemoteService references a local service: " + service.getUri());
        }

        this.reporter = reporter;
        parent = reporter.getCurrentActivity();
        remote = service;
        subscription = JerseyClientFactory.get(service).getEventSource("/activities").register(this::onMessage);

        JerseyClientFactory.setProxyUuid(proxyUuid);
    }

    private void onMessage(InboundSseEvent event) {
        List<ActivitySnapshot> activities = event.readData(ActivitySnapshot.LIST_TYPE);

        for (ActivitySnapshot snap : activities) {
            // only allow acitivities which have the proxy scope set, i.e. created by a remote call with our scope set.
            if (snap.scope == null || snap.scope.isEmpty()) {
                continue; // no scope set, cannot be interesting for us.
            }
            if (!snap.scope.get(0).equals(proxyUuid)) {
                continue;
            }

            String mappedUuid = uuidMapping.computeIfAbsent(snap.uuid, (s) -> UuidHelper.randomId());
            String mappedParentUuid = snap.parentUuid == null ? null
                    : uuidMapping.computeIfAbsent(snap.parentUuid, (s) -> UuidHelper.randomId());

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
        toRemove.forEach(x -> proxiedActivities.remove(x));
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
        ActivityNode node = new ActivityNode(snap, parent, this::onDone, this::onCancel, mappedUuid, parentUuid, proxyUuid);
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

    private void onDone(JerseySseActivity activity) {
        reporter.removeProxyActivity(activity);
    }

    private void onCancel(JerseySseActivity activity) {
        // cancel requested - delegate to remote
        String actualUuid = uuidMapping.entrySet().stream().filter(e -> e.getValue().equals(activity.getUuid())).findFirst()
                .map(e -> e.getKey()).orElse(null);
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

        subscription.close();
    }

    private static class ActivityNode {

        public JerseySseActivity activity;
        public long current = 0;
        public long max = -1;

        public ActivityNode(ActivitySnapshot snapshot, JerseySseActivity root, Consumer<JerseySseActivity> onDone,
                Consumer<JerseySseActivity> onCancel, String uuid, String parentUuid, String proxyUuid) {
            this.current = snapshot.current;
            this.max = snapshot.max;

            // remove the proxy scope, which MUST be the first element in the scope list.
            List<String> scope = snapshot.scope.subList(1, snapshot.scope.size());

            this.activity = new JerseySseActivity(onDone, onCancel, snapshot.name, () -> max, () -> current, scope,
                    root == null ? snapshot.user : root.getUser(), System.currentTimeMillis() - snapshot.duration, uuid,
                    parentUuid);
        }
    }

}

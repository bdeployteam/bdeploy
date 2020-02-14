package io.bdeploy.jersey.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;

/**
 * An activity reporter which exposes currently running activities to be broadcasted via SSE
 */
@Service
public class JerseyBroadcastingActivityReporter implements ActivityReporter {

    public static final String ACTIVITY_BROADCASTER = "JerseyActivityBroadcaster";

    private static final Logger log = LoggerFactory.getLogger(JerseyBroadcastingActivityReporter.class);
    static final ThreadLocal<JerseyRemoteActivity> currentActivity = new ThreadLocal<>();

    @Inject
    private JerseyScopeService jss;

    @Inject
    @Named(ACTIVITY_BROADCASTER)
    @Optional
    private JerseyEventBroadcaster bc;

    private boolean lastBroadcastWasEmpty;

    @Inject
    public JerseyBroadcastingActivityReporter(@Named(JerseyServer.BROADCAST_EXECUTOR) ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(this::sendUpdate, 1, 1, TimeUnit.SECONDS);
    }

    private void sendUpdate() {
        if (bc == null) {
            return;
        }

        List<ActivitySnapshot> list = getGlobalActivities().stream().filter(Objects::nonNull).map(JerseyRemoteActivity::snapshot)
                .collect(Collectors.toList());

        if (list.isEmpty() && lastBroadcastWasEmpty) {
            return;
        }

        lastBroadcastWasEmpty = list.isEmpty();

        bc.send(list);
    }

    /**
     * All running activities.
     */
    private final List<JerseyRemoteActivity> globalActivities = new ArrayList<>();

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

        JerseyRemoteActivity act = new JerseyRemoteActivity(this::done, activity, maxValue, currentValue, scope, user);
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
                JerseyRemoteActivity parent = globalActivities.stream().filter(Objects::nonNull)
                        .filter(a -> a.getUuid().equals(act.getParentUuid())).findFirst().orElse(null);
                if (parent != null) {
                    currentActivity.set(parent);
                } else {
                    log.warn("Parent activity no longer available: {}", act);
                }
            } else {
                // no parent set - we are top-level.
                currentActivity.set(null);
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

    JerseyRemoteActivity getActivityById(String uuid) {
        return globalActivities.stream().filter(Objects::nonNull).filter(a -> a.getUuid().equals(uuid)).findAny().orElse(null);
    }

}

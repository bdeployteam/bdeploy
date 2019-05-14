package io.bdeploy.jersey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;

/**
 * An activity reporter which exposes currently running activities to be broadcasted via SSE
 */
@Service
public class JerseySseActivityReporter implements ActivityReporter {

    private static final Logger log = LoggerFactory.getLogger(JerseySseActivityReporter.class);
    static final ThreadLocal<JerseySseActivity> currentActivity = new ThreadLocal<>();

    Consumer<JerseySseActivity> onDone = this::done;

    /**
     * All running activities.
     */
    private final List<JerseySseActivity> globalActivities = new ArrayList<>();

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
        ContainerRequestContext rqc = JerseyMultiThreadingContextBridge.current();
        List<String> scope = JerseySseActivityScopeFilter.getRequestActivityScope(rqc);
        String user = "<Unknown>";
        if (rqc != null) {
            SecurityContext sec = rqc.getSecurityContext();
            if (sec != null && sec.getUserPrincipal() != null) {
                user = sec.getUserPrincipal().getName();
            }
        }

        JerseySseActivity act = new JerseySseActivity(onDone, activity, maxValue, currentValue, scope, user);
        globalActivities.add(act);
        return act;
    }

    private synchronized void done(JerseySseActivity act) {
        if (!globalActivities.contains(act)) {
            return; // already done.
        }

        JerseySseActivity current = currentActivity.get();
        if (current != null && current.getUuid().equals(act.getUuid())) {
            // current is the one we're finishing
            if (act.getParentUuid() != null) {
                JerseySseActivity parent = globalActivities.stream().filter(Objects::nonNull)
                        .filter(a -> a.getUuid().equals(act.getParentUuid())).findFirst().orElse(null);
                if (parent != null) {
                    currentActivity.set(parent);
                } else {
                    log.warn("Parent activity no longer available: " + act);
                }
            } else {
                // no parent set - we are top-level.
                currentActivity.set(null);
            }
        } else if (current != null) {
            // we're finishing something which is not current -> warn & ignore
            log.warn("Finished activity is not current for this thread: " + act + ", current: " + current);
        } else {
            log.warn("Finished activity but there is no current activity for this thread: " + act);
        }

        globalActivities.remove(act);
    }

    @Override
    public NoThrowAutoCloseable proxyActivities(RemoteService service) {
        return new JerseySseActivityProxy(service, this);
    }

    /**
     * @return (a copy of) all currently known activities
     */
    synchronized List<JerseySseActivity> getGlobalActivities() {
        return new ArrayList<>(globalActivities);
    }

    synchronized void addProxyActivity(JerseySseActivity act) {
        globalActivities.add(act);
    }

    synchronized void removeProxyActivity(JerseySseActivity act) {
        globalActivities.remove(act);
    }

    /**
     * @return the current activity on the calling thread.
     */
    JerseySseActivity getCurrentActivity() {
        return currentActivity.get();
    }

    JerseySseActivity getActivityById(String uuid) {
        return globalActivities.stream().filter(Objects::nonNull).filter(a -> a.getUuid().equals(uuid)).findAny().orElse(null);
    }

}

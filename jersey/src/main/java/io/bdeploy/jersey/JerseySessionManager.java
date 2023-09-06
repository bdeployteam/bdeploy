package io.bdeploy.jersey;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.UuidHelper;

public class JerseySessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(JerseySessionManager.class);

    private final Cache<String, String> sessions;
    private final SessionStorage storage;

    private Future<?> storeJob;
    private final ScheduledExecutorService saveSched = Executors.newScheduledThreadPool(1,
            new NamedDaemonThreadFactory("Session Storage Persistence"));

    private final Cache<String, String> activeInPeriod = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    public JerseySessionManager(JerseySessionConfiguration config) {
        this.storage = config.storage;

        sessions = CacheBuilder.newBuilder().expireAfterWrite(config.sessionTimeout, TimeUnit.HOURS)
                .expireAfterAccess(config.sessionActiveTimeout, TimeUnit.HOURS).build();

        if (storage != null) {
            // In case we are restarting and sessions are restored, we cannot have active session timeout,
            // or better: the timeout is reset as if the user was just active. This is acceptable :)
            sessions.putAll(storage.load());
        }

        // save every 5 minutes to have *something* persisted in case the server suffers a stroke :)
        // an orderly shutdown will anyhow save the current sessions to disc.
        saveSched.scheduleAtFixedRate(this::syncSave, 5, 5, TimeUnit.MINUTES);
    }

    private synchronized void syncSave() {
        if (storage != null) {
            log.debug("Persisting session storage");
            storage.save(sessions.asMap());
        }
    }

    @Override
    public void close() {
        if (storeJob != null && !storeJob.isDone()) {
            storeJob.cancel(true);
        }

        syncSave();
        saveSched.shutdownNow();
    }

    @Override
    public synchronized String createSession(String token) {
        String id = UuidHelper.randomId();
        while (sessions.asMap().containsKey(id)) {
            id = UuidHelper.randomId();
        }
        sessions.put(id, token);

        if (log.isDebugEnabled()) {
            log.debug("Created session {}", id);
        }

        return id;
    }

    @Override
    public String getSessionToken(String session) {
        String sess = sessions.getIfPresent(session);
        if (sess != null) {
            try {
                activeInPeriod.get(session, () -> session);
            } catch (Exception x) {
                // cannot happen.
                log.error("Huh?", x);
            }
        }
        return sess;
    }

    @Override
    public Set<String> getActiveSessions() {
        return new TreeSet<>(activeInPeriod.asMap().keySet());
    }

    @Override
    public synchronized void removeSession(String session) {
        sessions.invalidate(session);

        if (log.isDebugEnabled()) {
            log.debug("Removed session {}", session);
        }
    }

}

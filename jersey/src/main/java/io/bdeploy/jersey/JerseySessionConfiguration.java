package io.bdeploy.jersey;

public class JerseySessionConfiguration {

    /**
     * Optional persistent storage for sessions.
     */
    public final SessionStorage storage;

    /**
     * The timeout (in hours) after which a session will be dismissed, even if the user was active in the meantime.
     */
    public final int sessionTimeout;

    /**
     * The timeout (in hours) within which a user must be active to keep the session alive.
     */
    public final int sessionActiveTimeout;

    private JerseySessionConfiguration(SessionStorage storage, int timeout, int activeTimeout) {
        this.storage = storage;
        this.sessionTimeout = timeout;
        this.sessionActiveTimeout = activeTimeout;
    }

    public static JerseySessionConfiguration noSessions() {
        return new JerseySessionConfiguration(null, 0, 0);
    }

    public static JerseySessionConfiguration withStorage(SessionStorage storage, int timeout, int activeTimeout) {
        return new JerseySessionConfiguration(storage, timeout, activeTimeout);
    }

}

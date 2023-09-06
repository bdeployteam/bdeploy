package io.bdeploy.jersey;

import java.util.Set;

import io.bdeploy.common.security.ApiAccessToken;

/**
 * The session manager - as the name suggests - keeps track of user session on the web interface.
 */
public interface SessionManager extends AutoCloseable {

    /**
     * The name of the cookie set containing the session ID.
     */
    public static final String SESSION_COOKIE = "st";

    /**
     * @param session the session ID
     * @return the associated {@link ApiAccessToken} in encrypted form (as used in Bearer headers).
     */
    public String getSessionToken(String session);

    /**
     * @param token the token to create a session for.
     * @return the ID of the new session.
     */
    public String createSession(String token);

    /**
     * @param session the session ID to invalidate.
     */
    public void removeSession(String session);

    /**
     * @return a set of active sessions in the past period (currently 5 minutes).
     */
    public Set<String> getActiveSessions();

    /**
     * Closes and cleans up the session manager.
     */
    @Override
    public void close();
}

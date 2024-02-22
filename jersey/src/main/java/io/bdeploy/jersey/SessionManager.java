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
     * Creates a new session with an one time password which can be used for a single login.
     *
     * @param token The token to create a session for
     * @return The one time password
     */
    public String createSessionWithOtp(String token);

    /**
     * Checks if a session with the given one time password exists. If one is found, the one time password is used up and
     * invalidated. This method will therefore only return <code>true</code> once per given OTP (unless another equal OTP is
     * created coincidentally, which is <i>very</i> unlikely).
     *
     * @param otp The one time password to check
     * @return The ID of the session of one was found, otherwise <code>null</code>
     */
    public String checkSessionOtp(String otp);

    /**
     * Closes and cleans up the session manager.
     */
    @Override
    public void close();
}

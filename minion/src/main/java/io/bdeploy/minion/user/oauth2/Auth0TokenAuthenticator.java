package io.bdeploy.minion.user.oauth2;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.Auth0SettingsDto;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.minion.user.AuthTrace;
import io.bdeploy.minion.user.Authenticator;

public class Auth0TokenAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(Auth0TokenAuthenticator.class);
    private static final String AUTH0_SYSTEM = SpecialAuthenticators.AUTH0.name();

    private final Cache<String, Boolean> validityCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        if (settings.auth0Settings == null || !settings.auth0Settings.enabled) {
            return false;
        }
        return user.external && AUTH0_SYSTEM.equals(user.externalSystem);
    }

    @Override
    public boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings) {
        if (user.inactive || !settings.auth0Settings.enabled) {
            return false;
        }

        if (validityCache.getIfPresent(user.name) != null) {
            return true; // we check every minute.
        }

        try {
            var info = performRequest(settings.auth0Settings, user.externalTag, null);
            if (info == null) {
                throw new IllegalStateException("Cannot verify login info");
            }
            // yay!
            validityCache.put(user.name, Boolean.TRUE);
        } catch (Exception e) {
            // nope, nope, nope
            return false;
        }

        return true;
    }

    @Override
    public UserInfo getInitialInfo(String username, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        return findAuthenticateUpdate(new UserInfo(username), password, settings, trace);
    }

    @Override
    public UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        if (!isResponsible(user, settings)) {
            return null;
        }

        return findAuthenticateUpdate(user, password, settings, trace);
    }

    /**
     * Queries configured server for the given user, authenticates it and updates the given user with information from the server.
     *
     * @param user the user to check, will be updated with additional info on success.
     * @param password the password to check
     * @param settings the servers to query
     * @param trace collector for tracing information
     * @return the successfully authenticated user, or <code>null</code> if not successful.
     */
    private static UserInfo findAuthenticateUpdate(UserInfo user, char[] password, AuthenticationSettingsDto settings,
            AuthTrace trace) {
        trace.log("  verify auth0 token for " + user.name);
        try {
            UserInfo found = performUserSearch(user, password, settings.auth0Settings, trace);
            if (found != null) {
                return found;
            }
        } catch (Exception e) {
            trace.log("    server " + settings.auth0Settings.domain + " failed: " + e.getMessage());
            log.debug("Cannot authenticate {} on server {}", user.name, settings.auth0Settings.domain);
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }
        }
        return null;
    }

    private static UserInfo performUserSearch(UserInfo user, char[] password, Auth0SettingsDto server, AuthTrace trace) {
        // Credentials of the user to be authenticated
        return verifyAndUpdateSearchResult(user, String.valueOf(password),
                performRequest(server, String.valueOf(password), trace));
    }

    private static Auth0UserInfo performRequest(Auth0SettingsDto settings, String token, AuthTrace trace) {
        try {
            var jcf = JerseyClientFactory.get(new URI("https://" + settings.domain + "/userinfo"), token);
            return jcf.getBaseTarget().request().get(Auth0UserInfo.class);
        } catch (Exception e) {
            if (trace != null) {
                trace.log("Cannot validate token: " + e.toString());
            }
            log.debug("Exception while validating token", e);
            return null;
        }
    }

    private static UserInfo verifyAndUpdateSearchResult(UserInfo user, String token, Auth0UserInfo u) {
        if (u == null) {
            return null;
        }

        user.external = true;
        user.externalSystem = AUTH0_SYSTEM;
        user.externalTag = token;

        user.fullName = u.name;
        user.email = u.email;

        return user;
    }

    private static final class Auth0UserInfo {

        public String name;
        public String email;

    }

}

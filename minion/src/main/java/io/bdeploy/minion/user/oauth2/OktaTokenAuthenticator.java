package io.bdeploy.minion.user.oauth2;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.OktaSettingsDto;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.minion.user.AuthTrace;
import io.bdeploy.minion.user.Authenticator;

public class OktaTokenAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(OktaTokenAuthenticator.class);
    private static final String OKTA_SYSTEM = SpecialAuthenticators.OKTA.name();

    private final Cache<String, Boolean> validityCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        if (settings.oktaSettings == null || !settings.oktaSettings.enabled) {
            return false;
        }
        return user.external && OKTA_SYSTEM.equals(user.externalSystem);
    }

    @Override
    public boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings) {
        if (user.inactive || !settings.oktaSettings.enabled) {
            return false;
        }

        if (validityCache.getIfPresent(user.name) != null) {
            return true; // we check every minute.
        }

        try {
            OktaAccessTokenInfo tokens = JacksonHelper.getDefaultJsonObjectMapper().readValue(String.valueOf(user.externalTag),
                    OktaAccessTokenInfo.class);
            var info = performRequest(user, settings.oktaSettings, tokens, null);
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
    private UserInfo findAuthenticateUpdate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        trace.log("  verify okta token for " + user.name);
        try {
            UserInfo found = performUserSearch(user, password, settings.oktaSettings, trace);
            if (found != null) {
                return found;
            }
        } catch (Exception e) {
            trace.log("    server " + settings.oktaSettings.domain + " failed: " + e.getMessage());
            log.debug("Cannot authenticate {} on server {}", user.name, settings.oktaSettings.domain);
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }
        }
        return null;
    }

    private UserInfo performUserSearch(UserInfo user, char[] password, OktaSettingsDto server, AuthTrace trace) throws Exception {
        // Credentials of the user to be authenticated
        OktaAccessTokenInfo info = JacksonHelper.getDefaultJsonObjectMapper().readValue(String.valueOf(password),
                OktaAccessTokenInfo.class);
        return verifyAndUpdateSearchResult(user, String.valueOf(password), performRequest(user, server, info, trace));
    }

    private OktaUserInfo performRequest(UserInfo user, OktaSettingsDto settings, OktaAccessTokenInfo tokens, AuthTrace trace) {
        try {
            var jcf = JerseyClientFactory.get(new URI(tokens.userinfoUrl), tokens.accessToken);

            String result = jcf.getBaseTarget().request().get(String.class);
            return JacksonHelper.getDefaultJsonObjectMapper().readValue(result, OktaUserInfo.class);
        } catch (Exception e) {
            if (trace != null) {
                trace.log("Cannot validate token: " + e.toString());
            }
            log.debug("Exception while validating token", e);
            return null;
        }
    }

    private UserInfo verifyAndUpdateSearchResult(UserInfo user, String token, OktaUserInfo u) {
        if (u == null) {
            return null;
        }

        user.external = true;
        user.externalSystem = OKTA_SYSTEM;
        user.externalTag = token;

        user.fullName = u.name;
        user.email = u.email;

        return user;
    }

    private static final class OktaUserInfo {

        public String name;
        public String email;

    }

    private static final class OktaAccessTokenInfo {

        String accessToken;
        String userinfoUrl;

    }

}

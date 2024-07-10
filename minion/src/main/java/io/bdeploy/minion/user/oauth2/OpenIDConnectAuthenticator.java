package io.bdeploy.minion.user.oauth2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.ResourceOwnerPasswordCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;
import io.bdeploy.interfaces.settings.OIDCSettingsDto;
import io.bdeploy.minion.user.AuthTrace;
import io.bdeploy.minion.user.Authenticator;

public class OpenIDConnectAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(OpenIDConnectAuthenticator.class);
    private static final String OIDC_SYSTEM = "OIDC";
    private final Map<String, CompletableFuture<Boolean>> refreshes = new TreeMap<>();

    @Override
    public boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings) {
        if (settings.oidcSettings == null || !settings.oidcSettings.enabled) {
            return false;
        }
        return user.external && OIDC_SYSTEM.equals(user.externalSystem);
    }

    @Override
    public boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings) {
        if (user.inactive || !settings.oidcSettings.enabled) {
            return false;
        }

        // make sure multiple requests from the same user only refresh the token once.
        // the first arriving thread does it, the others wait while it is doing so.
        CompletableFuture<Boolean> refresh;
        boolean doIt = false;
        synchronized (this) {
            refresh = refreshes.get(user.name);

            if (refresh == null) {
                if (log.isTraceEnabled()) {
                    log.trace("First to refresh {}", user.name);
                }
                // we are the ones doing it, yay.
                doIt = true;
                refresh = new CompletableFuture<>();
                refreshes.put(user.name, refresh);

                // since we're the boss, once we're done, remove the future.
                refresh.thenRun(() -> {
                    synchronized (OpenIDConnectAuthenticator.this) {
                        refreshes.remove(user.name);
                        if (log.isTraceEnabled()) {
                            log.trace("Removed refresh for {}", user.name);
                        }
                    }
                });
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Waiting for other to refresh {}", user.name);
                }
            }
        }

        if (doIt) {
            if (log.isTraceEnabled()) {
                log.trace("Actually refreshing {}", user.name);
            }
            try {
                refresh.complete(validateToken(user, settings.oidcSettings));
            } catch (Exception e) {
                refresh.completeExceptionally(e);
            }
        }

        try {
            Boolean valid = refresh.get();
            if (log.isTraceEnabled()) {
                log.trace("Got refresh result for {}: {}", user.name, valid);
            }
            return valid;
        } catch (InterruptedException e) {
            log.warn("Interrupted while validating authentication for {}", user.name, e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warn("Exception while validating authentication for {}", user.name, e);
        }
        return false;
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
     * Queries configured server for the given user, authenticates it and updates
     * the given user with information from the server.
     *
     * @param user the user to check, will be updated with additional info on
     *            success.
     * @param password the password to check
     * @param settings the servers to query
     * @param trace collector for tracing information
     * @return the successfully authenticated user, or <code>null</code> if not
     *         successful.
     */
    private UserInfo findAuthenticateUpdate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace) {
        trace.log("  query server " + settings.oidcSettings.url);
        try {
            UserInfo found = performUserSearch(user, password, settings.oidcSettings, trace);
            if (found != null) {
                return found;
            }
        } catch (Exception e) {
            trace.log("    server " + settings.oidcSettings.url + " failed: " + e.getMessage());
            log.debug("Cannot authenticate {} on server {}", user.name, settings.oidcSettings.url);
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }
        }
        return null;
    }

    private UserInfo performUserSearch(UserInfo user, char[] password, OIDCSettingsDto server, AuthTrace trace)
            throws ParseException, IOException, URISyntaxException {
        // Credentials of the user to be authenticated
        Secret pwd = new Secret(String.valueOf(password));
        AuthorizationGrant grant = new ResourceOwnerPasswordCredentialsGrant(user.name, pwd);

        return verifyAndUpdateSearchResult(user, performRequest(user, server, grant, trace));
    }

    private OIDCTokenResponse performRequest(UserInfo user, OIDCSettingsDto server, AuthorizationGrant grant, AuthTrace trace)
            throws ParseException, IOException, URISyntaxException {
        // Configured credentials which allow BDeploy to connect to the OIDC endpoint.
        ClientID clientID = new ClientID(server.client);
        Secret clientSecret = new Secret(server.secret);
        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);

        // Configured scope and URL for the OIDC endpoint.
        Scope scope = new Scope("openid", "email", "profile", "offline_access");
        URI tokenEndpoint = new URI(server.url);

        // Request which combines all this. The response is expected to be an OIDC
        // response, not just plain OAuth2
        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, grant, scope);
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

        if (!tokenResponse.indicatesSuccess()) {
            // We got an error response...
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
            if (log.isDebugEnabled()) {
                log.debug("Failed to authenticate {} against {}: {}: {}", user.name, server.url,
                        errorResponse.getErrorObject().getCode(), errorResponse.getErrorObject().getDescription());
            }
            if (trace != null) {
                trace.log("    request failed: " + errorResponse.getErrorObject().getCode() + ": "
                        + errorResponse.getErrorObject().getDescription());
            }
            return null;
        }

        return (OIDCTokenResponse) tokenResponse.toSuccessResponse();
    }

    private UserInfo verifyAndUpdateSearchResult(UserInfo user, OIDCTokenResponse response) {
        if (response == null) {
            return null;
        }

        user.external = true;
        user.externalSystem = OIDC_SYSTEM;
        user.externalTag = toExternalTag(response);

        try {
            JWTClaimsSet claims = response.getOIDCTokens().getIDToken().getJWTClaimsSet();

            user.fullName = claims.getStringClaim("name");
            user.email = claims.getStringClaim("email");
        } catch (Exception e) {
            log.warn("Failed to parse response claims in OIDC token", e);
        }

        return user;
    }

    private String toExternalTag(OIDCTokenResponse r) {
        return new String(
                StorageHelper.toRawBytes(
                        new OIDCTokenCache(r.getOIDCTokens().getIDTokenString(), r.getTokens().getRefreshToken().toString())),
                StandardCharsets.UTF_8);
    }

    private OIDCTokenCache fromExternalTag(String tag) {
        return StorageHelper.fromRawBytes(tag.getBytes(StandardCharsets.UTF_8), OIDCTokenCache.class);
    }

    /**
     * Validates the existing token - either by looking at the timeout, of the JWT,
     * or by querying the server using a refresh
     * token.
     *
     * @param user the user to check
     * @param settings the configuration including the OIDC settings
     * @return <code>true</code> if auth is valid, <code>false</code> otherwise,
     *         never planned to throw.
     */
    private boolean validateToken(UserInfo user, OIDCSettingsDto settings) {
        OIDCTokenCache cached = fromExternalTag(user.externalTag);
        JWT jwt = getJwt(cached.token);

        if (jwt == null) {
            return false;
        }

        try {
            Date expires = jwt.getJWTClaimsSet().getDateClaim(JWTClaimNames.EXPIRATION_TIME);
            if (expires == null || new Date().after(expires)) {
                if (log.isDebugEnabled()) {
                    log.debug("Stored OIDC token expired for {}", user.name);
                }

                if (cached.refresh == null) {
                    return false;
                }

                // try to refresh the authentication
                AuthorizationGrant grant = new RefreshTokenGrant(new RefreshToken(cached.refresh));
                try {
                    if (verifyAndUpdateSearchResult(user, performRequest(user, settings, grant, null)) == null) {
                        log.warn("Cannot refresh token for {}, unknown error", user.name);
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Refreshed OIDC token from server for {}", user.name);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("Cannot refresh token for {}: {}", user.name, e.toString());
                    if (log.isDebugEnabled()) {
                        log.debug("Exception:", e);
                    }
                }

                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to parse stored OIDC token for {}", user.name);
            return false;
        }

        return true;
    }

    private JWT getJwt(String token) {
        try {
            return JWTParser.parse(token);
        } catch (Exception e) {
            log.warn("Cannot parse stored JWT");
            return null;
        }
    }

    private static final class OIDCTokenCache {

        public String token;
        private final String refresh;

        @JsonCreator
        public OIDCTokenCache(@JsonProperty("token") String token, @JsonProperty("refresh") String refresh) {
            this.token = token;
            this.refresh = refresh;
        }
    }
}

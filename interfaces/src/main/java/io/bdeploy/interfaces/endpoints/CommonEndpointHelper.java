package io.bdeploy.interfaces.endpoints;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;

import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpAuthenticationType;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.ApplicationParameterValueResolver;
import io.bdeploy.interfaces.variables.ApplicationVariableResolver;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.ConditionalExpressionResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.EscapeJsonCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeXmlCharactersResolver;
import io.bdeploy.interfaces.variables.EscapeYamlCharactersResolver;
import io.bdeploy.interfaces.variables.InstanceAndSystemVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.interfaces.variables.Resolvers;
import io.bdeploy.jersey.TrustAllServersTrustManager;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;

public class CommonEndpointHelper {

    static {
        // you don't want to know. if you do, see DCS-417 or https://github.com/eclipse-ee4j/jersey/issues/3293
        HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    private static final Logger log = LoggerFactory.getLogger(CommonEndpointHelper.class);

    private CommonEndpointHelper() {
        // static helper only.
    }

    public static String initUri(HttpEndpoint endpoint, String hostname, String subPath) {
        return ((Boolean.valueOf(endpoint.secure.getPreRenderable()) == Boolean.TRUE) ? "https://" : "http://") + hostname
                + (endpoint.port != null ? (":" + endpoint.port.getPreRenderable()) : "")
                + concatWithSlashes(endpoint.path != null ? endpoint.path.getPreRenderable() : null, subPath);
    }

    private static String concatWithSlashes(String p1, String p2) {
        String r;

        p1 = p1 == null ? "" : p1;
        p2 = p2 == null ? "" : p2;

        if (p2.isEmpty()) {
            r = p1; // no second part.
        } else if (p1.endsWith("/") && p2.startsWith("/")) {
            r = p1 + p2.substring(1); // both have the slash
        } else if (p1.endsWith("/") || p2.startsWith("/")) {
            r = p1 + p2; // one has the slash
        } else {
            r = p1 + "/" + p2; // no slashes, add one.
        }

        if (r.startsWith("/")) {
            return r;
        } else {
            return "/" + r;
        }
    }

    public static Invocation.Builder createRequestBuilder(HttpEndpoint endpoint, String subPath, Map<String, Object> properties,
            Map<String, List<String>> queryParameters) throws GeneralSecurityException {
        ClientBuilder client = ClientBuilder.newBuilder();

        SSLContext sslContext = null;
        HostnameVerifier hv = null;
        if (Boolean.valueOf(endpoint.secure.getPreRenderable()) == Boolean.TRUE && endpoint.trustAll) {
            sslContext = SSLContext.getInstance("TLS");

            sslContext.init(null, new TrustManager[] { new TrustAllServersTrustManager() }, new java.security.SecureRandom());

            hv = (s1, s2) -> true;
            client.sslContext(sslContext).hostnameVerifier(hv);
        } else if (Boolean.valueOf(endpoint.secure.getPreRenderable()) == Boolean.TRUE
                && endpoint.trustStore.getPreRenderable() != null && !endpoint.trustStore.getPreRenderable().isEmpty()) {
            Path ksPath = Paths.get(endpoint.trustStore.getPreRenderable());

            char[] pp = null;
            if (endpoint.trustStorePass.getPreRenderable() != null && !endpoint.trustStorePass.getPreRenderable().isEmpty()) {
                pp = endpoint.trustStorePass.getPreRenderable().toCharArray();
            }

            KeyStore ks;
            try {
                ks = SecurityHelper.getInstance().loadPublicKeyStore(ksPath, pp);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);

                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);
                client.sslContext(sslContext);
            } catch (GeneralSecurityException | IOException e) {
                log.error("Cannot load configures trust store from {}", ksPath, e);
            }
        }

        HttpAuthenticationType authType;
        try {
            authType = HttpAuthenticationType.valueOf(endpoint.authType.getPreRenderable());
        } catch (Exception e) {
            log.warn("Invalid authentication type on endpoint {}: {}", endpoint.id, endpoint.authType.getPreRenderable(), e);
            authType = HttpAuthenticationType.NONE;
        }

        switch (authType) {
            case OIDC: // Nothing to do -> token will be added to header at a later point
            case NONE:
                break;
            case BASIC:
                client.register(HttpAuthenticationFeature.basic(endpoint.authUser.getPreRenderable(),
                        endpoint.authPass.getPreRenderable()));
                break;
            case DIGEST:
                client.register(HttpAuthenticationFeature.digest(endpoint.authUser.getPreRenderable(),
                        endpoint.authPass.getPreRenderable()));
                break;
            default:
                log.error("Unknown authentication type: {}", authType);
                break;
        }

        // client is always used locally, so we use localhost as hostname to avoid contacting somebody else unintentionally.
        WebTarget target = client.build().target(initUri(endpoint, "localhost", subPath));
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            target = target.property(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            target = target.queryParam(entry.getKey(), entry.getValue().toArray());
        }

        Builder builder = target.request();
        if (authType == HttpAuthenticationType.OIDC) {
            String tokenUrl = endpoint.tokenUrl.getPreRenderable();
            String clientId = endpoint.clientId.getPreRenderable();
            String clientSecret = endpoint.clientSecret.getPreRenderable();
            try {
                OIDCTokenResponse tokenResponse = performOIDCTokenRequest(tokenUrl, clientId, clientSecret, sslContext, hv);
                if (tokenResponse != null) {
                    builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.getOIDCTokens().getBearerAccessToken());
                }
            } catch (ParseException | URISyntaxException e) {
                log.warn("Failed to parse endpoint data.", e);
            } catch (IOException e) {
                log.error("Failed to send HTTP request.", e);
            }
        }
        return builder;
    }

    private static OIDCTokenResponse performOIDCTokenRequest(String tokenUrl, String clientId, String clientSecret,
            SSLContext sslContext, HostnameVerifier hv) throws ParseException, IOException, URISyntaxException {
        // Configured credentials which allow BDeploy to connect to the OIDC endpoint
        ClientID clientID = new ClientID(clientId);
        Secret secret = new Secret(clientSecret);
        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, secret);

        // Configured scope and URL for the OIDC endpoint
        Scope scope = new Scope("openid", "email", "profile", "offline_access");
        URI tokenEndpoint = new URI(tokenUrl);

        // Request which combines all this. The response is expected to be an OIDC response, not just plain OAuth2
        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, new ClientCredentialsGrant(), scope);

        HTTPRequest httpRequest = request.toHTTPRequest();
        if (sslContext != null) {
            httpRequest.setSSLSocketFactory(sslContext.getSocketFactory());
        }
        if (hv != null) {
            httpRequest.setHostnameVerifier(hv);
        }
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(httpRequest.send());

        if (!tokenResponse.indicatesSuccess()) {
            // We got an error response...
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
            log.info("Failed to authenticate against {}: {}: {}", tokenUrl, errorResponse.getErrorObject().getCode(),
                    errorResponse.getErrorObject().getDescription());
            return null;
        }

        return (OIDCTokenResponse) tokenResponse.toSuccessResponse();
    }

    /**
     * Processes and resolves all expressions on the endpoint.
     *
     * @param resolver the resolver to use.
     * @param rawEndpoint the raw endpoint to process and resolve.
     * @return the processed endpoint or null in case the endpoint cannot be enabled.
     */
    public static HttpEndpoint processEndpoint(VariableResolver resolver, HttpEndpoint rawEndpoint) {
        UnaryOperator<String> p = s -> TemplateHelper.process(s, resolver);

        // check if the endpoint is enabled, otherwise return null.
        try {
            LinkedValueConfiguration enabled = process(rawEndpoint.enabled, p);
            String pr = enabled.getPreRenderable();
            if (pr == null || pr.isBlank() || "false".equals(pr)) {
                return null;
            }
        } catch (Exception e) {
            // if we cannot process, we regard as *not* enabled.
            if (log.isTraceEnabled()) {
                log.trace("Failed to process endpoint {}", rawEndpoint, e);
            }
            return null;
        }

        HttpEndpoint processed = new HttpEndpoint();
        processed.id = rawEndpoint.id;
        processed.path = process(rawEndpoint.path, p);
        processed.contextPath = process(rawEndpoint.contextPath, p);
        processed.port = process(rawEndpoint.port, p);
        processed.secure = process(rawEndpoint.secure, p);
        processed.trustAll = rawEndpoint.trustAll;
        processed.trustStore = process(rawEndpoint.trustStore, p);
        processed.trustStorePass = process(rawEndpoint.trustStorePass, p);
        processed.authType = process(rawEndpoint.authType, p);
        processed.authUser = process(rawEndpoint.authUser, p);
        processed.authPass = process(rawEndpoint.authPass, p);
        processed.tokenUrl = process(rawEndpoint.tokenUrl, p);
        processed.clientId = process(rawEndpoint.clientId, p);
        processed.clientSecret = process(rawEndpoint.clientSecret, p);
        processed.proxying = rawEndpoint.proxying;

        return processed;
    }

    private static LinkedValueConfiguration process(LinkedValueConfiguration value, UnaryOperator<String> p) {
        if (value == null) {
            return new LinkedValueConfiguration(null);
        }
        return new LinkedValueConfiguration(p.apply(value.getPreRenderable()));
    }

    public static CompositeResolver createEndpoindResolver(InstanceNodeManifest inm, ApplicationConfiguration app,
            DeploymentPathProvider dpp) {
        return Resolvers.forApplication(Resolvers.forInstance(inm, dpp), inm.getConfiguration(), app);
    }
}

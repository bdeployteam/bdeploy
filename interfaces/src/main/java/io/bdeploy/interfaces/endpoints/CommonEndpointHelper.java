package io.bdeploy.interfaces.endpoints;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.function.UnaryOperator;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpAuthenticationType;
import io.bdeploy.jersey.TrustAllServersTrustManager;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

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
                + concatWithSlashes(endpoint.path, subPath);
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

    public static WebTarget initClient(HttpEndpoint endpoint, String subPath) throws GeneralSecurityException {
        ClientBuilder client = ClientBuilder.newBuilder();

        if (Boolean.valueOf(endpoint.secure.getPreRenderable()) == Boolean.TRUE && endpoint.trustAll) {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new TrustAllServersTrustManager() }, new java.security.SecureRandom());

            client.sslContext(sslcontext).hostnameVerifier((s1, s2) -> true);
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

                SSLContext sslContext = SSLContext.getInstance("TLS");
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
            log.warn("Invalid authentication type on endpoint {}: {}", endpoint.id, endpoint.authType.getPreRenderable());
            authType = HttpAuthenticationType.NONE;
        }

        if (authType == HttpAuthenticationType.BASIC) {
            client.register(
                    HttpAuthenticationFeature.basic(endpoint.authUser.getPreRenderable(), endpoint.authPass.getPreRenderable()));
        } else if (authType == HttpAuthenticationType.DIGEST) {
            client.register(
                    HttpAuthenticationFeature.digest(endpoint.authUser.getPreRenderable(), endpoint.authPass.getPreRenderable()));
        }

        // client is always used locally, so we use localhost as hostname to avoid contacting somebody else unintentionally.
        return client.build().target(initUri(endpoint, "localhost", subPath));
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
            return null;
        }

        HttpEndpoint processed = new HttpEndpoint();
        processed.id = rawEndpoint.id;
        processed.path = rawEndpoint.path;
        processed.contextPath = rawEndpoint.contextPath;
        processed.port = process(rawEndpoint.port, p);
        processed.secure = process(rawEndpoint.secure, p);
        processed.trustAll = rawEndpoint.trustAll;
        processed.trustStore = process(rawEndpoint.trustStore, p);
        processed.trustStorePass = process(rawEndpoint.trustStorePass, p);
        processed.authType = process(rawEndpoint.authType, p);
        processed.authUser = process(rawEndpoint.authUser, p);
        processed.authPass = process(rawEndpoint.authPass, p);
        processed.proxying = rawEndpoint.proxying;

        return processed;
    }

    private static LinkedValueConfiguration process(LinkedValueConfiguration value, UnaryOperator<String> p) {
        if (value == null) {
            return new LinkedValueConfiguration(null);
        }
        return new LinkedValueConfiguration(p.apply(value.getPreRenderable()));
    }

}

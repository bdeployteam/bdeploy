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
        return (endpoint.secure ? "https://" : "http://") + hostname + (endpoint.port != null ? (":" + endpoint.port) : "")
                + concatWithSlashes(endpoint.path, subPath);
    }

    private static String concatWithSlashes(String p1, String p2) {
        String r;

        p1 = p1 == null ? "" : p1;
        p2 = p2 == null ? "" : p2;

        if (p1.endsWith("/") && p2.startsWith("/")) {
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

        if (endpoint.secure && endpoint.trustAll) {
            SSLContext sslcontext = SSLContext.getInstance("TLS");

            sslcontext.init(null, new TrustManager[] { new TrustAllServersTrustManager() }, new java.security.SecureRandom());

            client.sslContext(sslcontext).hostnameVerifier((s1, s2) -> true);
        } else if (endpoint.secure && endpoint.trustStore != null && !endpoint.trustStore.isEmpty()) {
            Path ksPath = Paths.get(endpoint.trustStore);

            char[] pp = null;
            if (endpoint.trustStorePass != null && !endpoint.trustStorePass.isEmpty()) {
                pp = endpoint.trustStorePass.toCharArray();
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

        if (endpoint.authType == HttpAuthenticationType.BASIC) {
            client.register(HttpAuthenticationFeature.basic(endpoint.authUser, endpoint.authPass));
        } else if (endpoint.authType == HttpAuthenticationType.DIGEST) {
            client.register(HttpAuthenticationFeature.digest(endpoint.authUser, endpoint.authPass));
        }

        // client is always used locally, so we use localhost as hostname to avoid contacting somebody else unintentionally.
        return client.build().target(initUri(endpoint, "localhost", subPath));
    }

    public static HttpEndpoint processEndpoint(VariableResolver resolver, HttpEndpoint rawEndpoint) {
        HttpEndpoint processed = new HttpEndpoint();

        UnaryOperator<String> p = s -> TemplateHelper.process(s, resolver);

        processed.id = rawEndpoint.id;
        processed.path = rawEndpoint.path;
        processed.contextPath = rawEndpoint.contextPath;
        processed.port = p.apply(rawEndpoint.port);
        processed.secure = rawEndpoint.secure;
        processed.trustAll = rawEndpoint.trustAll;
        processed.trustStore = p.apply(rawEndpoint.trustStore);
        processed.trustStorePass = p.apply(rawEndpoint.trustStorePass);
        processed.authType = rawEndpoint.authType;
        processed.authUser = p.apply(rawEndpoint.authUser);
        processed.authPass = p.apply(rawEndpoint.authPass);
        processed.proxying = rawEndpoint.proxying;

        return processed;
    }

}

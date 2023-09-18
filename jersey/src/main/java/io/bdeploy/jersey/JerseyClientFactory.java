package io.bdeploy.jersey;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.GZipEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketUpgradeHandler;

import io.bdeploy.common.security.CompositeX509TrustManager;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientListener;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.ext.Provider;

/**
 * A factory for Jersey based JAX-RS clients.
 */
public class JerseyClientFactory {

    static {
        // you don't want to know. if you do, see DCS-417 or https://github.com/eclipse-ee4j/jersey/issues/3293
        HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    private static final Logger log = LoggerFactory.getLogger(JerseyClientFactory.class);

    private SSLContext sslContext;
    private String bearer;
    private final RemoteService svc;

    private final Set<com.fasterxml.jackson.databind.Module> additionalModules = new HashSet<>();
    private JerseyObjectMapper mapperFeature;
    private WebTarget cachedTarget;

    private static final Cache<RemoteService, JerseyClientFactory> factoryCache = CacheBuilder.newBuilder().maximumSize(100)
            .expireAfterAccess(5, TimeUnit.MINUTES).build();

    /**
     * @param svc the {@link RemoteService} specification to create clients for.
     */
    private JerseyClientFactory(RemoteService svc) {
        this.svc = svc;
        try {
            SecurityHelper sec = SecurityHelper.getInstance();
            bearer = sec.getSignedToken(svc.getKeyStore().getStore(), svc.getKeyStore().getPass());

            // composite of default trust manager (for official certificates), and the target server's
            // self-signed internal certificate (part of the authentication token).
            TrustManager[] tm = CompositeX509TrustManager.getTrustManagers(svc.getKeyStore().getStore());

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tm, null);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot initialize security", e);
        }
    }

    private JerseyClientFactory(RemoteService svc, String bearer) {
        this.svc = svc;
        this.bearer = bearer;
        try {
            sslContext = SSLContext.getDefault();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot initialize security", e);
        }
    }

    public static JerseyClientFactory get(RemoteService svc) {
        try {
            return factoryCache.get(svc, () -> new JerseyClientFactory(svc));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot create/get client factory", e);
        }
    }

    public static JerseyClientFactory get(URI uri, String bearer) {
        RemoteService tmp = new RemoteService(uri, "");
        try {
            return factoryCache.get(tmp, () -> new JerseyClientFactory(tmp, bearer));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot create/get client factory", e);
        }
    }

    public static void invalidateCached(RemoteService srv) {
        factoryCache.invalidate(srv);
    }

    public synchronized void register(com.fasterxml.jackson.databind.Module o) {
        if (additionalModules.contains(o)) {
            return;
        }

        cachedTarget = null;
        additionalModules.add(o);

        // reset.
        mapperFeature = null;
    }

    /**
     * @return the configured {@link SSLContext} used by this factory. This is only
     *         for testing.
     */
    SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * @param clazz the type of resource (service interface).
     * @return A dynamic proxy based client for the given remote service.
     */
    public <T> T getProxyClient(Class<T> clazz, Object... additionalRegistrations) {
        Path path = clazz.getAnnotation(Path.class);
        // Sub-Resources that are provided by another resource have - by convention - no path declared
        // Trying to directly resolve them could lead to troubles that are hard to discover and debug
        if (path == null) {
            log.error("Resource '{}' does not have a @Path annotation."
                    + "Seems to be a sub-resource that needs to be queried via a parent resource.", clazz);
        }
        return WebResourceFactory.newResource(clazz, getBaseTarget(additionalRegistrations));
    }

    /**
     * @return a {@link WebTarget} with all required feature, filter and provider
     *         registrations for the {@link RemoteService} associated with this
     *         factory.
     */
    public synchronized WebTarget getBaseTarget(Object... additionalRegistrations) {
        if (additionalRegistrations.length == 0 && cachedTarget != null) {
            return cachedTarget;
        }

        ClientBuilder builder = ClientBuilder.newBuilder();

        // for HttpUrlConnection to allow restricted headers, see https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/client.html#d0e4971
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        // 30 seconds to connect must be enough. NEVER set ANY read timeout. Otherwise closing bogus HTTPS connections can block
        // ALL other HTTPS connections, see sun.security.ssl.SSLSocketImpl.AppInputStream.readLockedDeplete().
        builder.connectTimeout(30, TimeUnit.SECONDS);

        builder.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.CHUNKED);

        builder.sslContext(sslContext);
        builder.hostnameVerifier((h, s) -> true);

        if (mapperFeature == null) {
            mapperFeature = new JerseyObjectMapper(additionalModules);
        }

        builder.register(mapperFeature);
        builder.register(GZipEncoder.class);
        builder.register(JerseyGZipFilter.class);

        builder.register(JacksonFeature.class);
        builder.register(MultiPartFeature.class);
        builder.register(new ClientBearerFilter(bearer));
        builder.register(JerseyPathReader.class);
        builder.register(JerseyPathWriter.class);

        for (Object reg : additionalRegistrations) {
            if (reg instanceof Class<?>) {
                builder.register((Class<?>) reg);
            } else {
                builder.register(reg);
            }
        }

        WebTarget target = builder.build().target(svc.getUri());

        if (additionalRegistrations.length == 0) {
            // cache the target
            cachedTarget = target;
        }

        return target;
    }

    /**
     * @return a client that is capable of providing a WebSocket connection to the given service. The caller is responsible for
     *         closing the client once done!
     */
    public AsyncHttpClient getWebSocketClient() {
        // using custom hostname verifier is not possible when using sslContext, need to trust all for now.
        return new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setAcceptAnyCertificate(true).build());
    }

    /**
     * Create a {@link ObjectChangeClientWebSocket} which allows to subscribe to object changes, and notifies about them.
     */
    public ObjectChangeClientWebSocket getObjectChangeWebSocket(Consumer<ObjectChangeDto> onChanges) {
        AsyncHttpClient client = getWebSocketClient();
        ListenableFuture<WebSocket> ws = client
                .prepareGet(
                        svc.getWebSocketUri(ObjectChangeWebSocket.OCWS_PATH).toString())
                .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new ObjectChangeClientListener(
                        SecurityHelper.getInstance().getTokenFromPack(svc.getAuthPack()), onChanges)).build());

        try {
            return new ObjectChangeClientWebSocket(client, ws.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot open WebSocket (interrupted)", ie);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open WebSocket", e);
        }
    }

    @Provider
    private static class ClientBearerFilter implements ClientRequestFilter {

        private final String bearerToken;

        public ClientBearerFilter(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.getHeaders().add("Authorization", "Bearer " + bearerToken);
        }
    }

}

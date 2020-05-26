package io.bdeploy.jersey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

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

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.jersey.activity.JerseyRemoteActivityScopeClientFilter;
import io.bdeploy.jersey.ws.WebSocketAuthenticatingMessageListener;

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
    private ActivityReporter reporter = new ActivityReporter.Null();
    private final Set<com.fasterxml.jackson.databind.Module> additionalModules = new HashSet<>();
    private WebTarget cachedTarget;

    private static final ThreadLocal<String> proxyUuid = new ThreadLocal<>();
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

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(svc.getKeyStore().getStore());

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
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

    public synchronized void register(com.fasterxml.jackson.databind.Module o) {
        if (additionalModules.contains(o)) {
            return;
        }

        cachedTarget = null;
        additionalModules.add(o);
    }

    /**
     * @param reporter the {@link ActivityReporter} to report lengthy operations to
     *            (e.g. up/download).
     */
    public void setReporter(ActivityReporter reporter) {
        this.reporter = reporter;
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

        builder.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.CHUNKED);

        builder.sslContext(sslContext);
        builder.hostnameVerifier((h, s) -> true);
        builder.register(new JerseyObjectMapper(additionalModules));

        builder.register(GZipEncoder.class);
        builder.register(JerseyGZipFilter.class);

        builder.register(JacksonFeature.class);
        builder.register(MultiPartFeature.class);
        builder.register(new ClientBearerFilter(bearer));
        builder.register(JerseyPathReader.class);
        builder.register(JerseyPathWriter.class);
        builder.register(new JerseyClientReporterResolver());
        builder.register(new JerseyRemoteActivityScopeClientFilter(proxyUuid::get));

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

    public static void setProxyUuid(String uuid) {
        proxyUuid.set(uuid);
    }

    public static String getProxyUuid() {
        return proxyUuid.get();
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
     * @param client the client to use, obtain from {@link #getWebSocketClient()}
     * @param scope the scope to filter messages with on the server side.
     * @param path the path on the server under the '/ws' context
     * @param onMessage callback for received messaged
     * @param onError callback for received errors
     * @return a {@link ListenableFuture} which can be used to retrieve the {@link WebSocket}.
     */
    public ListenableFuture<WebSocket> getAuthenticatedWebSocket(AsyncHttpClient client, List<String> scope, String path,
            Consumer<byte[]> onMessage, Consumer<Throwable> onError, Consumer<WebSocket> onClose) {
        return client.prepareGet(svc.getWebSocketUri(path).toString())
                .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new WebSocketAuthenticatingMessageListener(
                        SecurityHelper.getInstance().getTokenFromPack(svc.getAuthPack()), scope, onMessage, onError, onClose))
                        .build());
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

    @Provider
    private final class JerseyClientReporterResolver implements ContextResolver<ActivityReporter> {

        @Override
        public ActivityReporter getContext(Class<?> type) {
            return reporter;
        }

    }

}

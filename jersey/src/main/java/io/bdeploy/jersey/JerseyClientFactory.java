package io.bdeploy.jersey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.sse.SseEventSource;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;

/**
 * A factory for Jersey based JAX-RS clients.
 */
public class JerseyClientFactory {

    private static final Logger log = LoggerFactory.getLogger(JerseyClientFactory.class);

    private SSLContext sslContext;
    private String bearer;
    private final RemoteService svc;
    private ActivityReporter reporter = new ActivityReporter.Null();
    private final Set<com.fasterxml.jackson.databind.Module> additionalModules = new HashSet<>();
    private WebTarget cachedTarget;

    private static final ThreadLocal<String> proxyUuid = new ThreadLocal<>();
    private static final Cache<RemoteService, JerseyClientFactory> factoryCache = CacheBuilder.newBuilder().maximumSize(100)
            .expireAfterAccess(5, TimeUnit.MINUTES).removalListener(i -> {
                JerseyClientFactory jcf = (JerseyClientFactory) i.getValue();
                jcf.close();
            }).build();

    private final Cache<String, JerseyCachedEventSource> sseCache = CacheBuilder.newBuilder().maximumSize(20)
            .removalListener((i) -> {
                JerseyCachedEventSource ses = (JerseyCachedEventSource) i.getValue();
                ses.doExpire();
            }).build();

    private final ScheduledExecutorService sseCacheReaper;

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

        this.sseCacheReaper = Executors
                .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("SSE Client Cache Reaper - " + svc.getUri()));
        this.sseCacheReaper.scheduleAtFixedRate(this::cacheReaper, 30, 30, TimeUnit.SECONDS);
    }

    private synchronized void cacheReaper() {
        Set<String> toEvict = new TreeSet<>();
        for (Map.Entry<String, JerseyCachedEventSource> entry : sseCache.asMap().entrySet()) {
            if (entry.getValue().isExpired()) {
                toEvict.add(entry.getKey());
            }
        }
        sseCache.invalidateAll(toEvict);
    }

    /**
     * Perform maintenance cleanup tasks now, used for testing
     */
    public void cleanUp() {
        cacheReaper();
    }

    private void close() {
        sseCacheReaper.shutdownNow();
        sseCache.invalidateAll();
    }

    public static JerseyClientFactory get(RemoteService svc) {
        try {
            return factoryCache.get(svc, () -> new JerseyClientFactory(svc));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot create/get client factory", e);
        }
    }

    public void register(com.fasterxml.jackson.databind.Module o) {
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
            log.error("Resource '" + clazz + "' does not have a @Path annotation."
                    + "Seems to be a sub-resource that needs to be queried via a parent resource.");
        }
        return WebResourceFactory.newResource(clazz, getBaseTarget(additionalRegistrations));
    }

    /**
     * Note: proxy based clients are not suitable for {@link SseEventSource}s. Use
     * {@link #getEventSource(String)} instead.
     *
     * @return a {@link WebTarget} with all required feature, filter and provider
     *         registrations for the {@link RemoteService} associated with this
     *         factory.
     */
    public WebTarget getBaseTarget(Object... additionalRegistrations) {
        if (additionalRegistrations.length == 0 && cachedTarget != null) {
            return cachedTarget;
        }

        ClientBuilder builder = ClientBuilder.newBuilder();

        builder.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.CHUNKED);

        builder.sslContext(sslContext);
        builder.hostnameVerifier((h, s) -> true);
        builder.register(new JerseyObjectMapper(additionalModules));
        builder.register(JacksonFeature.class);
        builder.register(MultiPartFeature.class);
        builder.register(new ClientBearerFilter(bearer));
        builder.register(JerseyPathReader.class);
        builder.register(JerseyPathWriter.class);
        builder.register(new JerseyClientReporterResolver());
        builder.register(new JerseySseActivityProxyClientFilter(() -> proxyUuid.get()));

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

    static void setProxyUuid(String uuid) {
        proxyUuid.set(uuid);
    }

    /**
     * @param path the path to the SSE endpoint relative to the
     *            {@link RemoteService} URI.
     * @return An {@link SseEventSource} which allows listening to server sent
     *         events. The returned {@link SseEventSource} might already be open as it might have been cached. In this case
     *         further {@link SseEventSource#open()} calls are ignored.
     */
    public synchronized JerseySseRegistrar getEventSource(String path) {
        try {
            WebTarget target = getBaseTarget().path(path);
            return sseCache.get(path, () -> new JerseyCachedEventSource(SseEventSource.target(target).build(), target));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot load event source from cache", e);
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

    @Provider
    private final class JerseyClientReporterResolver implements ContextResolver<ActivityReporter> {

        @Override
        public ActivityReporter getContext(Class<?> type) {
            return reporter;
        }

    }

}

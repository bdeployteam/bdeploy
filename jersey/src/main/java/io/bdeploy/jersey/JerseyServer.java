package io.bdeploy.jersey;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.jersey.JerseyAuthenticationProvider.JerseyAuthenticationUnprovider;
import io.bdeploy.jersey.JerseyAuthenticationProvider.JerseyAuthenticationWeakenerProvider;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.Log4jAuditor;
import io.bdeploy.jersey.resources.JerseyMetricsResourceImpl;

/**
 * Encapsulates required functionality from the Grizzly HttpServer with the
 * Jersey handlers.
 * <p>
 * Use {@link #register(Object)} to register additional resource, filters and
 * providers before starting the server.
 * <p>
 * {@link #registerRoot(HttpHandler)} can be used to register a Grizzly
 * {@link HttpHandler} for the root context ("/") of the server. This can be
 * used to register a web application using e.g. {@link CLStaticHttpHandler}.
 */
public class JerseyServer implements AutoCloseable, RegistrationTarget {

    public static final Logger log = LoggerFactory.getLogger(JerseyServer.class);

    public static final String START_TIME = "StartTime";
    public static final String TOKEN_SIGNER = "TokenSigner";
    public static final String BROADCAST_EXECUTOR = "BcExecutor";

    private final int port;
    private final ResourceConfig rc = new ResourceConfig();
    private final KeyStore store;
    private final char[] passphrase;
    private final Instant startTime = Instant.now();
    private final Collection<AutoCloseable> closeableResources = new ArrayList<>();
    private final ActivityReporter.Delegating reporterDelegate = new ActivityReporter.Delegating();

    private final AtomicLong broadcasterId = new AtomicLong(0);
    private final ScheduledExecutorService broadcastScheduler = Executors.newScheduledThreadPool(1,
            new NamedDaemonThreadFactory(() -> "Scheduled Broadcast " + broadcasterId.incrementAndGet()));

    private HttpServer server;
    private HttpHandler root;
    private Auditor auditor = new Log4jAuditor();

    /**
     * @param port the port to listen on
     * @param store the keystore carrying the private certificate/key material
     *            for SSL.
     * @param passphrase the passphrase for the keystore.
     */
    public JerseyServer(int port, KeyStore store, char[] passphrase) {
        this.port = port;
        this.store = store;
        this.passphrase = passphrase.clone();

        // Grizzly uses JUL
        if (!SLF4JBridgeHandler.isInstalled()) {
            // level of JUL is controlled with the level for the own logger
            Level target = Level.WARNING;
            if (log.isInfoEnabled()) {
                target = Level.INFO;
            }
            if (log.isDebugEnabled()) {
                target = Level.FINE;
            }
            if (log.isTraceEnabled()) {
                target = Level.FINER;
                // not finest, as this breaks grizzly.
            }
            java.util.logging.Logger.getLogger("").setLevel(target);
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }

    /**
     * @return an {@link ActivityReporter} which can broadcast to remote.
     */
    public ActivityReporter getSseActivityReporter() {
        return reporterDelegate;
    }

    /**
     * Sets the auditor that will be used by the server to log requests. The auditor will be closed
     * when the server is terminated.
     *
     * @param auditor
     *            auditor to log requests
     */
    public void setAuditor(Auditor auditor) {
        this.auditor = auditor;
        registerResource(auditor);
    }

    @Override
    public void registerResource(AutoCloseable closeable) {
        closeableResources.add(closeable);
    }

    /**
     * Registers a class or an instance to be used in this server.
     *
     * @param provider a {@link Class} or {@link Object} instance to register. Also
     *            supports registration of custom {@link Binder} instances
     *            which allow custom dependency injection in services.
     */
    @Override
    public void register(Object provider) {
        if (provider instanceof Class<?>) {
            rc.register((Class<?>) provider);
        } else {
            rc.register(provider);
        }
    }

    /**
     * @param handler the root ("/") context path handler.
     */
    @Override
    public void registerRoot(HttpHandler handler) {
        root = handler;
    }

    /**
     * Start the server as configured.
     */
    public void start() {
        try {
            URI jerseyUri = UriBuilder.fromUri("https://0.0.0.0/api").port(port).build();

            // SSL
            KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmfactory.init(store, passphrase);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmfactory.getKeyManagers(), null, null);

            SSLEngineConfigurator sslEngine = new SSLEngineConfigurator(ctx, false, false, false);
            sslEngine.setEnabledProtocols(new String[] { "TLSv1", "TLSv1.1", "TLSv1.2" });

            // default features
            rc.register(new ServerObjectBinder());
            rc.register(JerseyObjectMapper.class);
            rc.register(JacksonFeature.class);
            rc.register(MultiPartFeature.class);
            rc.register(new JerseyAuthenticationProvider(store));
            rc.register(JerseyAuthenticationUnprovider.class);
            rc.register(JerseyAuthenticationWeakenerProvider.class);
            rc.register(JerseyPathReader.class);
            rc.register(JerseyPathWriter.class);
            rc.register(JerseyMetricsFilter.class);
            rc.register(JerseyMetricsResourceImpl.class);
            rc.register(JerseyAuditingFilter.class);
            rc.register(JerseyExceptionMapper.class);
            rc.register(JerseySseActivityResourceImpl.class);
            rc.register(JerseySseActivityScopeFilter.class);
            rc.register(new JerseyLazySseInitializer());
            rc.register(new JerseyServerReporterContextResolver());
            rc.register(new JerseyWriteLockFilter());

            // disable output content buffer to allow "real" streaming. jersey will always use chunked encoding.
            rc.property(ServerProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, 0);

            server = GrizzlyHttpServerFactory.createHttpServer(jerseyUri, rc, true, sslEngine, false);
            if (root != null) {
                server.getServerConfiguration().addHttpHandler(root, HttpHandlerRegistration.ROOT);
            }

            server.getHttpHandler().setAllowEncodedSlash(true);
            server.start();

            log.info("Started Version " + VersionHelper.readVersion());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot start server", e);
        }
    }

    /**
     * @return whether the server is running.
     */
    public boolean isRunning() {
        return server != null && server.isStarted();
    }

    @Override
    public void close() {
        // Close all registered resources
        for (AutoCloseable closeable : closeableResources) {
            try {
                log.info("Closing resource '{}'", closeable);
                closeable.close();
                if (log.isDebugEnabled()) {
                    log.debug("Resource '{}' closed", closeable);
                }
            } catch (Exception ex) {
                log.error("Failed to close resource '{}'", closeable, ex);
            }
        }
        closeableResources.clear();
        // Shutdown the server itself
        if (server != null) {
            server.shutdownNow();
            server = null;
        }
    }

    public boolean join() {
        while (isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return isRunning();
            }
        }
        return isRunning();
    }

    private class ServerObjectBinder extends AbstractBinder {

        @Override
        protected void configure() {
            Function<ApiAccessToken, String> signer = a -> SecurityHelper.getInstance().createToken(a, store, passphrase);

            bind(JerseySseActivityReporter.class).in(Singleton.class).to(JerseySseActivityReporter.class);
            bind(JerseyWriteLockService.class).in(Singleton.class).to(JerseyWriteLockService.class);
            bind(startTime).named(START_TIME).to(Instant.class);
            bind(broadcastScheduler).named(BROADCAST_EXECUTOR).to(ScheduledExecutorService.class);
            bind(signer).named(TOKEN_SIGNER).to(new TypeLiteral<Function<ApiAccessToken, String>>() {
            });

            // need to lazily access the auditor in case it is changed later.
            bindFactory(new JerseyAuditorBridgeFactory()).to(Auditor.class);

            // need to bridge over to the same instance as used for the singleton sse activity reporter.
            bindFactory(JerseySseToActivityReporterBridgeFactory.class).to(ActivityReporter.class);
        }

    }

    /**
     * Provides the auditor for dependency injection in a dynamic fashion.
     */
    private class JerseyAuditorBridgeFactory implements Factory<Auditor> {

        @Override
        public Auditor provide() {
            return auditor;
        }

        @Override
        public void dispose(Auditor instance) {
            // nothing to do
        }

    }

    /**
     * Provides the instance of {@link JerseySseActivityReporter} when an {@link ActivityReporter} is requested for injection.
     */
    private static class JerseySseToActivityReporterBridgeFactory implements Factory<ActivityReporter> {

        @Inject
        private JerseySseActivityReporter reporter;

        @Override
        public ActivityReporter provide() {
            return reporter;
        }

        @Override
        public void dispose(ActivityReporter instance) {
            // nothing to do
        }

    }

    /**
     * Updates the delegate {@link ActivityReporter} of the {@link JerseyServer} to the resolved {@link JerseySseActivityReporter}
     * once it is available for injection.
     */
    private class JerseyLazySseInitializer implements ContainerLifecycleListener {

        @Override
        public void onStartup(Container container) {
            reporterDelegate.setDelegate(
                    container.getApplicationHandler().getInjectionManager().getInstance(JerseySseActivityReporter.class));
        }

        @Override
        public void onReload(Container container) {
            // delegate stays the same
        }

        @Override
        public void onShutdown(Container container) {
            // nothing to do
        }

    }

    /**
     * Provides the {@link ActivityReporter} delegate as JAX-RS context object, which is required by {@link Provider}s that want
     * to resolve the {@link ActivityReporter} from the JAX-RS context.
     */
    @Provider
    private class JerseyServerReporterContextResolver implements ContextResolver<ActivityReporter> {

        @Override
        public ActivityReporter getContext(Class<?> type) {
            return reporterDelegate;
        }

    }

}

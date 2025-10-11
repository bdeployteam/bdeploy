package io.bdeploy.jersey;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.websockets.WebSocketAddOn;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.audit.Slf4jAuditor;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.jersey.JerseyAuthenticationProvider.JerseyAuthenticationUnprovider;
import io.bdeploy.jersey.JerseyAuthenticationProvider.JerseyAuthenticationWeakenerProvider;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.errorpages.JerseyGrizzlyErrorPageGenerator;
import io.bdeploy.jersey.fs.FileSystemSpaceService;
import io.bdeploy.jersey.monitoring.JerseyServerMonitor;
import io.bdeploy.jersey.monitoring.JerseyServerMonitoringResourceImpl;
import io.bdeploy.jersey.monitoring.JerseyServerMonitoringSamplerService;
import io.bdeploy.jersey.resources.ActionResourceImpl;
import io.bdeploy.jersey.resources.JerseyMetricsResourceImpl;
import io.bdeploy.jersey.resources.RedirectOnApiRootAccessImpl;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Encapsulates required functionality from the Grizzly HttpServer with the
 * Jersey handlers.
 * <p>
 * Use {@link #register(Object)} to register additional resource, filters and
 * providers before starting the server.
 */
public class JerseyServer implements AutoCloseable, RegistrationTarget {

    /**
     * The "Content-Length"-Buffer is a buffer used to buffer a response and determine its length.
     * <p>
     * Once the buffer overflows, the server switches from settings a Content-Length header on a response to chunked transfer
     * encoding.
     * <p>
     * The buffer is intentionally very small to support streaming responses (e.g. ZIP files, ...).
     * <p>
     * The buffer size is also the limit for response sizes to exclude from compression. If compression would be be there,
     * we would set this to zero to completely disable buffering, but compression will /always/ happen for chunked encoding
     * as content length cannot be determined up front.
     */
    private static final int CL_BUFFER_SIZE = 512;

    private static final Logger log = LoggerFactory.getLogger(JerseyServer.class);

    /**
     * The enabled and supported cipher suites. This needs to be aligned with the "Intermediate compatibility"
     * recommendation by Mozilla: https://wiki.mozilla.org/Security/Server_Side_TLS#Intermediate_compatibility_.28recommended.29
     */
    // @formatter:off
    private static final String[] cipherSuites = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",

            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",

            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",

            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",

            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
    };
    // @formatter:on

    public static final String START_TIME = "StartTime";
    public static final String BROADCAST_EXECUTOR = "BcExecutor";
    public static final String FILE_SYSTEM_MIN_SPACE = "FileSystemMinSpace";

    private final int port;
    private final ResourceConfig rc = new ResourceConfig();
    private final KeyStore store;
    private final KeyStore httpsStore;
    private final char[] passphrase;
    private final Instant startTime = Instant.now();
    private final Collection<AutoCloseable> closeableResources = new ArrayList<>();
    private final CompletableFuture<RegistrationTarget> startup = new CompletableFuture<>();

    private final AtomicLong broadcasterId = new AtomicLong(0);
    private final ScheduledExecutorService broadcastScheduler = Executors.newScheduledThreadPool(1,
            new NamedDaemonThreadFactory(() -> "Scheduled Broadcast " + broadcasterId.incrementAndGet()));

    private final Map<HttpHandlerRegistration, HttpHandler> preRegistrations = new HashMap<>();
    private HttpServer server;
    private Auditor auditor = new Slf4jAuditor();
    private final JerseyServerMonitor monitor = new JerseyServerMonitor();
    private final JerseyServerMonitoringSamplerService serverMonitoring = new JerseyServerMonitoringSamplerService(monitor);
    private final JerseySessionManager sessionManager;
    private final Map<String, WebSocketApplication> wsApplications = new TreeMap<>();

    private Predicate<ApiAccessToken> tokenValidator;

    /**
     * @param port the port to listen on
     * @param store the keystore carrying the private certificate/key material
     *            for SSL.
     * @param passphrase the passphrase for the keystore.
     */
    public JerseyServer(int port, KeyStore store, KeyStore httpsStore, char[] passphrase, JerseySessionConfiguration sessions) {
        this.port = port;
        this.store = store;
        this.httpsStore = httpsStore;
        this.passphrase = passphrase.clone();
        this.sessionManager = new JerseySessionManager(sessions);
    }

    @Override
    public KeyStore getKeyStore() {
        return store;
    }

    @Override
    public CompletableFuture<RegistrationTarget> afterStartup() {
        return startup;
    }

    /**
     * Sets the auditor that will be used by the server to log requests.
     *
     * @param auditor
     *            auditor to log requests
     */
    public void setAuditor(Auditor auditor) {
        this.auditor = auditor;
    }

    /**
     * @param tokenValidator a {@link Predicate} which can verify the validity of an {@link ApiAccessToken}
     */
    public void setTokenValidator(Predicate<ApiAccessToken> tokenValidator) {
        this.tokenValidator = tokenValidator;
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
            // unfortunately, priorities are not respected correctly in all cases later on from annotations.
            Priority prio = ((Class<?>) provider).getAnnotation(Priority.class);
            if (prio != null) {
                rc.register((Class<?>) provider, prio.value());
            } else {
                rc.register((Class<?>) provider);
            }
        } else {
            rc.register(provider);
        }
    }

    @Override
    public void addHandler(HttpHandler handler, HttpHandlerRegistration registration) {
        if (server == null) {
            preRegistrations.put(registration, handler);
        } else {
            server.getServerConfiguration().addHttpHandler(handler, registration);
        }
    }

    @Override
    public void removeHandler(HttpHandler handler) {
        if (server == null) {
            // data is organized differently in grizzly. they remember a list of registrations per handler instead
            // of mapping registrations to handlers, which is way easier, since registrations have equals/hashCode anyway.
            Set<HttpHandlerRegistration> r = preRegistrations.entrySet().stream().filter(e -> e.getValue().equals(handler))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());

            r.forEach(preRegistrations::remove);
        } else {
            server.getServerConfiguration().removeHttpHandler(handler);
        }
    }

    @Override
    public void registerWebsocketApplication(String urlMapping, WebSocketApplication wsa) {
        wsApplications.put(urlMapping, wsa);
    }

    public static void updateLogging() {
        // Grizzly uses JUL
        if (SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.uninstall();
        }

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

    /**
     * Start the server as configured.
     */
    public void start() {
        try {
            URI jerseyUri = UriBuilder.fromUri("https://0.0.0.0/api").port(port).build();

            // SSL
            KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            if (httpsStore != null) {
                // dedicated HTTPS certificate to be used.
                kmfactory.init(httpsStore, passphrase);
            } else {
                // fallback to the default certificate (self-signed).
                kmfactory.init(store, passphrase);
            }

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmfactory.getKeyManagers(), null, null);

            SSLEngineConfigurator sslEngine = new SSLEngineConfigurator(ctx, false, false, false);
            sslEngine.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.3" });

            // in case we're running on java 11 (or any non-21 (currently) FWIW), we need to check
            // each cipher if it is supported.

            sslEngine.setEnabledCipherSuites(getSupportedCiphers(ctx, cipherSuites));

            // default features - also for plugins.
            registerDefaultResources(rc);

            // this redirects from /api to / - not in the default resources as we *do not* want this for plugins.
            rc.register(RedirectOnApiRootAccessImpl.class);

            GrizzlyHttpContainer container = ContainerFactory.createContainer(GrizzlyHttpContainer.class, rc);
            server = GrizzlyHttpServerFactory.createHttpServer(jerseyUri, container, true, sslEngine, false);
            for (Map.Entry<HttpHandlerRegistration, HttpHandler> regs : preRegistrations.entrySet()) {
                server.getServerConfiguration().addHttpHandler(regs.getValue(), regs.getKey());
            }

            monitor.setServer(server);
            monitor.setSessionManager(sessionManager);

            // register custom error page generator.
            server.getServerConfiguration().setDefaultErrorPageGenerator(new JerseyGrizzlyErrorPageGenerator());
            server.getServerConfiguration().setHttpServerName("BDeploy");
            server.getServerConfiguration().setHttpServerVersion(VersionHelper.getVersionAsString());

            WebSocketAddOn wsao = new WebSocketAddOn();
            for (NetworkListener listener : server.getListeners()) {
                // we want to have unrestricted thread counts to allow ALL requests to be processed in parallel.
                // otherwise in-vm communication can soft-lock the process (e.g. push hangs because the reading
                // thread is not started).
                final int coresCount = 8;
                ThreadPoolConfig cfg = ThreadPoolConfig.defaultConfig().setPoolName("BDeploy-Transport-Worker")
                        .setCorePoolSize(coresCount).setMaxPoolSize(Integer.MAX_VALUE)
                        .setMemoryManager(listener.getTransport().getMemoryManager());

                listener.getTransport().setWorkerThreadPoolConfig(cfg);

                // enable compression on the server for known mime types.
                CompressionConfig cc = listener.getCompressionConfig();

                cc.setCompressionMode(CompressionMode.ON);
                cc.setCompressionMinSize(CL_BUFFER_SIZE);
                cc.setDecompressionEnabled(true);

                // enable WebSockets on the listener
                listener.registerAddOn(wsao);

                // register content security policy (CSP) filter.
                listener.registerAddOn(new JerseyCspFilter.JerseyCspAddOn());
            }

            // register all WebSocketApplications on their path.
            wsApplications.forEach((path, app) -> WebSocketEngine.getEngine().register("/ws", path, app));
            server.getHttpHandler().setAllowEncodedSlash(true);
            server.start();

            log.info("Started Version {}", VersionHelper.getVersion());

            startup.complete(this);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot start server on " + port, e);
        }
    }

    private static String[] getSupportedCiphers(SSLContext ctx, String[] requested) {
        List<String> supported = new ArrayList<>();
        List<String> builtin = Arrays.asList(ctx.getServerSocketFactory().getSupportedCipherSuites());
        for (String cipher : requested) {
            if (builtin.contains(cipher)) {
                supported.add(cipher);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Ignoring unsupported cipher suite {}", cipher);
                }
            }
        }
        return supported.toArray(new String[supported.size()]);
    }

    /**
     * @param config a ResourceConfig to enrich with all the default resources and features used by the BDeploy JAX-RS
     *            infrastructure. Allows to create additional JAX-RS applications which use the same setup as BDeploy itself.
     *            This is useful e.g. for plugins which should use the same filters/features as BDeploy.
     */
    public void registerDefaultResources(ResourceConfig config) {
        config.register(new ServerObjectBinder());
        config.register(JerseyObjectMapper.class);
        config.register(JacksonFeature.withoutExceptionMappers());
        config.register(MultiPartFeature.class);

        // unfortunately, priorities annotated on the providers are not always respected.
        config.register(new JerseyAuthenticationProvider(store, tokenValidator, sessionManager), Priorities.AUTHENTICATION);
        config.register(JerseyAuthenticationUnprovider.class, Priorities.AUTHENTICATION - 1);
        config.register(JerseyAuthenticationWeakenerProvider.class, Priorities.AUTHENTICATION - 2);

        config.register(JerseyPathReader.class);
        config.register(JerseyPathWriter.class);
        config.register(JerseyMetricsFilter.class);
        config.register(JerseyMetricsResourceImpl.class);
        config.register(JerseyAuditingFilter.class);
        config.register(JerseyExceptionMapper.class);
        config.register(ActionResourceImpl.class);
        config.register(JerseyServerMonitoringResourceImpl.class);
        config.register(new JerseyWriteLockFilter());
        config.register(JerseyScopeFilter.class);

        config.property(ServerProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, CL_BUFFER_SIZE);
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

        // stop the session manager (and allow it to persist stuff).
        sessionManager.close();

        // Shutdown the server itself
        if (server != null) {
            server.shutdownNow();
            server = null;
        }
    }

    public boolean join() {
        while (isRunning()) {
            if (!Threads.sleep(1000)) {
                return isRunning();
            }
        }
        return isRunning();
    }

    private class ServerObjectBinder extends AbstractBinder {

        @Override
        protected void configure() {
            // NOTE: *DO NOT* create singleton resources here on the fly, since this binder is used
            // when initializing plugins as well. For every plugin loaded this might cause resource leaks.

            bind(JerseyWriteLockService.class).in(Singleton.class).to(JerseyWriteLockService.class);
            bind(JerseyScopeService.class).in(Singleton.class).to(JerseyScopeService.class);
            bind(FileSystemSpaceService.class).in(Singleton.class).to(FileSystemSpaceService.class);

            bind(ActionFactory.class).to(ActionFactory.class);

            bind(startTime).named(START_TIME).to(Instant.class);
            bind(broadcastScheduler).named(BROADCAST_EXECUTOR).to(ScheduledExecutorService.class);
            bind(serverMonitoring).to(JerseyServerMonitoringSamplerService.class);
            bind(sessionManager).to(SessionManager.class);

            // need to lazily access the auditor in case it is changed later.
            bindFactory(new JerseyAuditorBridgeFactory()).to(Auditor.class);
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
}

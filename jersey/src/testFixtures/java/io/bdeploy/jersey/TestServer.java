package io.bdeploy.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.UuidHelper;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;

/**
 * {@link TestServer} provides an embedded Grizzly/Jersey server with the
 * provided additional resource and provider registrations.
 * <p>
 * In addition to the actual server, unit test parameter injection is provided
 * for {@link JerseyClientFactory}, {@link RemoteService}.
 * <p>
 * Every registered resource's service interface can be injected into tests as
 * well, providing a proxy to a jersey client.
 * <p>
 * Additional registrations may be performed in an {@link BeforeEach} callback.
 */
public class TestServer
        implements RegistrationTarget, ParameterResolver, BeforeTestExecutionCallback, BeforeEachCallback, AfterEachCallback {

    public static final char[] TEST_STORE_PASS = "storepass".toCharArray();

    private final String serverUuid = UuidHelper.randomId();

    protected KeyStore serverStore;
    protected String authPack;
    protected char[] storePass = TEST_STORE_PASS;

    private final List<Object> registrations = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Map<String, WebSocketApplication> wsApplications = new TreeMap<>();
    private CompletionStage<RegistrationTarget> startup;

    private final Map<HttpHandlerRegistration, HttpHandler> handlers = new HashMap<>();
    private ServiceLocator rootLocator;
    private Predicate<ApiAccessToken> tokenValidator;
    private Auditor auditor;

    private RemoteService service;
    private int port;

    private final boolean resolver;

    public TestServer() {
        this(new Object[0]);
    }

    public TestServer(Object... registrations) {
        this(true, registrations);
    }

    public TestServer(boolean resolver, Object[] registrations) {
        this.resolver = resolver;
        this.registrations.addAll(Arrays.asList(registrations));
        this.registrations.add(new ServiceLocatorFinder());

        SecurityHelper helper = SecurityHelper.getInstance();
        try {
            try (InputStream is = TestServer.class.getClassLoader().getResourceAsStream("certstore.p12")) {
                serverStore = helper.loadPrivateKeyStore(is, TEST_STORE_PASS);
            }
            ApiAccessToken aat = new ApiAccessToken.Builder().setIssuedTo(System.getProperty("user.name"))
                    .addPermission(ScopedPermission.GLOBAL_ADMIN).build();
            authPack = helper.createSignaturePack(aat, serverStore, TEST_STORE_PASS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load keystores", e);
        }
    }

    protected void resetRegistrations() {
        registrations.clear();
        resources.clear();
    }

    @Override
    public KeyStore getKeyStore() {
        return serverStore;
    }

    public char[] getStorePass() {
        return storePass;
    }

    public void setTokenValidator(Predicate<ApiAccessToken> tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    public void setAuditor(Auditor auditor) {
        this.auditor = auditor;
        this.resources.add(auditor);
    }

    public Auditor getAuditor() {
        return auditor;
    }

    @Override
    public CompletionStage<RegistrationTarget> afterStartup() {
        return startup;
    }

    @Override
    public void register(Object o) {
        registrations.add(o);
    }

    @Override
    public void registerResource(AutoCloseable resource) {
        resources.add(resource);
    }

    @Override
    public void addHandler(HttpHandler handler, HttpHandlerRegistration reg) {
        handlers.put(reg, handler);
    }

    @Override
    public void removeHandler(HttpHandler handler) {
        Set<HttpHandlerRegistration> r = handlers.entrySet().stream().filter(e -> e.getValue().equals(handler))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        r.forEach(handlers::remove);
    }

    @Override
    public void registerWebsocketApplication(String urlMapping, WebSocketApplication wsa) {
        wsApplications.put(urlMapping, wsa);
    }

    private List<Class<?>> getRegisteredClasses() {
        return this.registrations.stream().map(r -> (Class<?>) ((r instanceof Class) ? r : r.getClass()))
                .collect(Collectors.toList());
    }

    protected Object getServerIdentifyingObject() {
        return null;
    }

    protected Object getParameterIdentifyingObject(ParameterContext context) {
        return null;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (!resolver) {
            return false;
        }

        if (getServerIdentifyingObject() != null && getParameterIdentifyingObject(parameterContext) != null
                && !Objects.equals(getServerIdentifyingObject(), getParameterIdentifyingObject(parameterContext))) {
            // all is set to distinguish servers, but no match -> nope.
            return false;
        }

        Class<?> type = parameterContext.getParameter().getType();
        if (type.isAssignableFrom(RemoteService.class) || type.isAssignableFrom(JerseyClientFactory.class)
                || type.isAssignableFrom(ServiceLocator.class)) {
            return true;
        }

        if (type.isAssignableFrom(getClass())) {
            return true;
        }

        for (Class<?> registration : getRegisteredClasses()) {
            if (type.isAssignableFrom(registration)) {
                return true;
            }
        }

        return false;
    }

    public RemoteService getRemoteService() {
        return service;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        CloseableServer closeableServer = getExtensionStore(extensionContext).get(CloseableServer.class, CloseableServer.class);
        Class<?> type = parameterContext.getParameter().getType();
        if (type.isAssignableFrom(RemoteService.class)) {
            return service;
        } else if (type.isAssignableFrom(JerseyClientFactory.class)) {
            return closeableServer.factory;
        } else if (type.isAssignableFrom(ServiceLocator.class)) {
            return rootLocator;
        } else if (type.isAssignableFrom(getClass())) {
            return this;
        } else {
            for (Class<?> registration : getRegisteredClasses()) {
                if (type.isAssignableFrom(registration)) {
                    return closeableServer.factory.getProxyClient(type);
                }
            }
        }
        throw new RuntimeException("Unsupported injection point: " + parameterContext);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        getExtensionStore(context).getOrComputeIfAbsent(CloseableServer.class,
                (k) -> new CloseableServer(getServerPort(context)));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (auditor != null) {
            auditor.close();
        }
    }

    public int getPort() {
        return this.port;
    }

    protected int getServerPort(ExtensionContext context) {
        return getExtensionStore(context).getOrComputeIfAbsent("ServerPort", (k) -> {
            this.port = findFreePort();
            return this.port;
        }, Integer.class);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        getExtensionStore(context).get(CloseableServer.class, CloseableServer.class).start();
    }

    protected Store getExtensionStore(ExtensionContext context) {
        return context.getStore(Namespace.create(context.getRequiredTestMethod(), serverUuid));
    }

    protected int findFreePort() {
        try (ServerSocket ss = new ServerSocket()) {
            ss.bind(null);
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot determin local port to use", e);
        }
    }

    public void disableAuth() {
        register(new JerseyAuthenticationDisabler());
    }

    protected final class CloseableServer implements CloseableResource {

        URI uri;
        JerseyServer server;
        private final JerseyClientFactory factory;

        public CloseableServer(int port) {
            this.uri = UriBuilder.fromUri("https://localhost:" + port + "/api").build();
            service = new RemoteService(uri, authPack);
            this.server = new JerseyServer(uri.getPort(), serverStore, null, storePass, JerseySessionConfiguration.noSessions());
            this.factory = JerseyClientFactory.get(service);
        }

        public void start() {
            resources.forEach(this.server::registerResource);
            registrations.forEach(this.server::register);
            wsApplications.forEach(this.server::registerWebsocketApplication);
            if (tokenValidator != null) {
                this.server.setTokenValidator(tokenValidator);
            }
            if (auditor != null) {
                this.server.setAuditor(auditor);
            }
            startup = server.afterStartup();
            handlers.forEach((r, h) -> this.server.addHandler(h, r));
            this.server.start();
        }

        public JerseyServer getServer() {
            return server;
        }

        @Override
        public void close() {
            startup = null;
            server.close();
        }
    }

    @Provider
    @Priority(Priorities.AUTHENTICATION - 2) // before auth
    public static class JerseyAuthenticationDisabler implements ContainerRequestFilter {

        @Override
        public void filter(ContainerRequestContext requestContext) {
            requestContext.setProperty("unsecured", "unsecured");
        }
    }

    private class ServiceLocatorFinder implements ContainerLifecycleListener {

        @Override
        public void onStartup(Container container) {
            rootLocator = container.getApplicationHandler().getInjectionManager().getInstance(ServiceLocator.class);
        }

        @Override
        public void onReload(Container container) {
        }

        @Override
        public void onShutdown(Container container) {
        }
    }
}

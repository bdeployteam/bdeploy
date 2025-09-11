package io.bdeploy.interfaces.remote;

import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration.LVCModule;
import io.bdeploy.interfaces.remote.versioning.VersionMismatchFilter;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Provides a {@link JerseyClientFactory} with all required configurations applied.
 */
public class ResourceProvider {

    private ResourceProvider() {
    }

    /**
     * Returns a client factory for the given remote minion.
     */
    public static JerseyClientFactory of(RemoteService svc) {
        JerseyClientFactory jcf = JerseyClientFactory.get(svc);
        jcf.register(JerseyRemoteBHive.HIVE_JACKSON_MODULE);
        jcf.register(LVCModule.LVC_MODULE); // we ONLY want this module when we are REST client.
        return jcf;
    }

    /**
     * Returns a proxy for the given service.
     *
     * @param service the remote to connect to
     * @param clazz the type of interface to connect to
     * @param caller the caller on whos behalf to act on. if <code>null</code>, the user of the token in the remote is used.
     */
    public static <T> T getResource(RemoteService service, Class<T> clazz, SecurityContext caller) {
        JerseyClientFactory factory = of(service);
        return factory.getProxyClient(clazz, new JerseyOnBehalfOfFilter(caller));
    }

    /**
     * Returns a proxy for the given service. The client is versioning aware, converting 404 errors to 499 errors if the server
     * version is different from the client version.
     *
     * @param service the remote to connect to
     * @param clazz the type of interface to connect to
     * @param caller the caller on whose behalf to act on. if <code>null</code>, the user of the token in the remote is used.
     */
    public static <T> T getVersionedResource(RemoteService service, Class<T> clazz, SecurityContext caller) {
        JerseyClientFactory factory = of(service);
        return factory.getProxyClient(clazz, new JerseyOnBehalfOfFilter(caller), new VersionMismatchFilter(factory));
    }

}

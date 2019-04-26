package io.bdeploy.interfaces.remote;

import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;

/**
 * Provides a {@link JerseyClientFactory} with all required configurations applied.
 */
public class ResourceProvider {

    /**
     * Returns a client factory for the given remote minion.
     */
    public static JerseyClientFactory of(RemoteService svc) {
        JerseyClientFactory jcf = JerseyClientFactory.get(svc);
        jcf.register(JerseyRemoteBHive.HIVE_JACKSON_MODULE);
        return jcf;
    }

    /**
     * Returns a proxy for the given service.
     */
    public static <T> T getResource(RemoteService service, Class<T> clazz) {
        JerseyClientFactory factory = of(service);
        return factory.getProxyClient(clazz);
    }

}

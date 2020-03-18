package io.bdeploy.jersey;

import javax.net.ssl.SSLContext;

/**
 * Helper to extract internal {@link SSLContext} information from a
 * {@link JerseyClientFactory}
 */
public class ClientSslContextAccessor {

    public static SSLContext get(JerseyClientFactory factory) {
        return factory.getSslContext();
    }

}

package io.bdeploy.jersey;

import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;

/**
 * A listener implementation which will request a certain service at container startup, so that it is initialized.
 */
public class JerseyEagerServiceInitializer<T> implements ContainerLifecycleListener {

    private final Class<T> clazz;

    public JerseyEagerServiceInitializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public void onStartup(Container container) {
        container.getApplicationHandler().getInjectionManager().getInstance(clazz);
    }

    @Override
    public void onReload(Container container) {
        onStartup(container);
    }

    @Override
    public void onShutdown(Container container) {
    }

}

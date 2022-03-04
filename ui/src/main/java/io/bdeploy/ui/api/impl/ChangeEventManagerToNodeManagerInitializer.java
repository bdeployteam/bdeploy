package io.bdeploy.ui.api.impl;

import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;

import io.bdeploy.ui.api.NodeManager;

public class ChangeEventManagerToNodeManagerInitializer implements ContainerLifecycleListener {

    private final NodeManager nodes;

    public ChangeEventManagerToNodeManagerInitializer(NodeManager nodes) {
        this.nodes = nodes;
    }

    @Override
    public void onStartup(Container container) {
        var changes = container.getApplicationHandler().getInjectionManager().getInstance(ChangeEventManager.class);
        nodes.setChangeEventManager(changes);
    }

    @Override
    public void onReload(Container container) {
        onStartup(container);
    }

    @Override
    public void onShutdown(Container container) {
        // intentionally blank
    }

}

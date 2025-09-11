package io.bdeploy.minion.remote.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.remote.NodeSyncResource;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;

public class NodeSyncResourceMultiImpl implements NodeSyncResource {

    private static final Logger log = LoggerFactory.getLogger(NodeSyncResourceMultiImpl.class);

    @Inject
    MinionRoot root;

    @Override
    public void synchronizationFinished() {
        log.info("Synchronization by master finished");

        // perform "missing" after startup sequence..
        root.afterStartup(false, false);
    }
}

package io.bdeploy.minion.remote.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.remote.NodeSyncResource;

public class NodeSyncResourceDummyImpl implements NodeSyncResource {

    private static final Logger log = LoggerFactory.getLogger(NodeSyncResourceDummyImpl.class);

    @Override
    public void synchronizationFinished() {
        // right now just a dummy for normal nodes.
        log.info("Synchronization by master finished");
    }
}

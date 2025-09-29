package io.bdeploy.minion.remote.jersey;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.remote.NodeSyncResource;
import io.bdeploy.minion.MinionRoot;
import jakarta.inject.Inject;

public class NodeSyncResourceMultiImpl implements NodeSyncResource {

    private static final Logger log = LoggerFactory.getLogger(NodeSyncResourceMultiImpl.class);
    private static final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    MinionRoot root;

    @Override
    public void synchronizationFinished() {
        log.info("Synchronization by master finished");

        if (running.compareAndSet(false, true)) {
            log.info("First synchronization detected, completing node startup");

            // perform "missing" after startup sequence..
            root.afterStartup(false, false);
        }
    }
}

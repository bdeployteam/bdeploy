package io.bdeploy.minion.nodes;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;

/**
 * Helper which handles the communication with a master to register a multi-node
 */
public class MultiNodeRegistration {

    private static final Logger log = LoggerFactory.getLogger(MultiNodeRegistration.class);
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static void register(MinionRoot root, String name, MinionDto dto, MultiNodeMasterFile mnmf) {
        // this is the first and so far ONLY time when a node may contact a master. thus all the required code is contained here.
        Runnable registration = () -> {
            // information regarding the master - this is what we want to connect to.
            RemoteService master = mnmf.master;
            String multiNodeId = mnmf.name;

            try {
                // need to "prematurely" initialize the node managed for the synchronization logic to work
                // (i.e. this will contact us from the master and we need to be able to answer).
                ((NodeManagerImpl) root.getNodeManager()).initialize(root, false);

                // contact the master :)
                MasterRootResource mrr = ResourceProvider.getVersionedResource(master, MasterRootResource.class, null);
                if (mrr.attachMultiNode(multiNodeId, name, running.compareAndSet(false, true), dto)) {
                    // nothing more to do. master will call us back once ready. until then, we just sit here.
                    log.info("Registration with master successful, waiting for synchronization...");
                }
            } catch (Exception e) {
                log.error("Registration with master failed", e);
            }
        };

        // we run the registration every 30 seconds, so that a loss in connection (which will discard the runtime node on the
        // master) will be recovered after a little while (will trigger a re-synchronization from the master).
        scheduler.scheduleAtFixedRate(registration, 0, 30, TimeUnit.SECONDS);
    }

}

package io.bdeploy.minion.nodes;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.util.StorageHelper;
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

    public static void register(MinionRoot root, MinionDto dto, MultiNodeMasterFile mnmf) {
        // this is the first and so far ONLY time when a node may contact a master. thus all the required code is contained here.

        // information regarding the master - this is what we want to connect to.
        RemoteService master = mnmf.master;
        String multiNodeId = mnmf.name;

        try {
            // need to "prematurely" initialize the node managed for the synchronization logic to work
            // (i.e. this will contact us from the master and we need to be able to answer).
            ((NodeManagerImpl) root.getNodeManager()).initialize(root, false);

            // contact the master :)
            MasterRootResource mrr = ResourceProvider.getVersionedResource(master, MasterRootResource.class, null);
            mrr.attachMultiNode(multiNodeId, dto);

            // nothing more to do. master will call us back once ready. until then, we just sit here.
            log.info("Registration with master successful, waiting for synchronization...");
        } catch (Exception e) {
            log.error("Registration with master failed", e);
        }
    }

}

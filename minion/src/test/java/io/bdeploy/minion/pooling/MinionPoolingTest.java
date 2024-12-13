package io.bdeploy.minion.pooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHive.Operation;
import io.bdeploy.bhive.BHivePoolOrganizer;
import io.bdeploy.bhive.CollectingConsumer;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestHive.class)
@ExtendWith(TestMinion.class)
class MinionPoolingTest {

    @Test
    void testPooling(@TempDir Path tmp, MinionRoot root, CommonRootResource master, RemoteService remote, BHive local,
            BHiveRegistry reg) throws Exception {
        Path pool = root.getRootDir().resolve("objpool"); // pool target of test server.

        setupGroup("GroupA", remote);
        setupGroup("GroupB", remote);

        assertEquals(pool, reg.get("GroupA").getPoolPath());
        assertEquals(pool, reg.get("GroupB").getPoolPath());

        ObjectDatabase poolDb = new ObjectDatabase(pool, pool.resolve("tmp"), new ActivityReporter.Null(), null);
        assertEquals(0, CollectingConsumer.collect(poolDb::walkAllObjects).size());

        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, false);

        local.execute(new PushOperation().setHiveName("GroupA").setRemote(remote).addManifest(instance));
        local.execute(new PushOperation().setHiveName("GroupB").setRemote(remote).addManifest(instance));

        // after pushing the BHives should both contain the manifest and objects should be consistent
        assertTrue(reg.get("GroupA").execute(new ManifestExistsOperation().setManifest(instance)));
        assertTrue(reg.get("GroupB").execute(new ManifestExistsOperation().setManifest(instance)));

        assertEquals(0, reg.get("GroupA").execute(new FsckOperation()).size());
        assertEquals(0, reg.get("GroupB").execute(new FsckOperation()).size());

        // move all objects which are referenced at least twice to the pool.
        BHivePoolOrganizer.reorganizeAll(reg, 2, root.getActions());

        assertFalse(CollectingConsumer.collect(poolDb::walkAllObjects).isEmpty());

        assertTrue(reg.get("GroupA").execute(new ManifestExistsOperation().setManifest(instance)));
        assertTrue(reg.get("GroupB").execute(new ManifestExistsOperation().setManifest(instance)));

        assertEquals(0, reg.get("GroupA").execute(new FsckOperation()).size());
        assertEquals(0, reg.get("GroupB").execute(new FsckOperation()).size());

        // both may still contain the instance group config object and its tree, but nothing more.
        assertEquals(2, reg.get("GroupA").execute(new InternalListAllObjectsOp()));
        assertEquals(2, reg.get("GroupB").execute(new InternalListAllObjectsOp()));
    }

    private static void setupGroup(String groupName, RemoteService remote) {
        InstanceGroupConfiguration cfg = new InstanceGroupConfiguration();

        cfg.name = groupName;
        cfg.description = groupName;
        cfg.title = groupName;

        ResourceProvider.getResource(remote, CommonRootResource.class, null).addInstanceGroup(cfg, null);
    }

    private static final class InternalListAllObjectsOp extends Operation<Integer> {

        @Override
        public Integer call() {
            return getObjectManager().db(db -> {
                try {
                    return CollectingConsumer.collect(db::walkAllObjects).size();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

}

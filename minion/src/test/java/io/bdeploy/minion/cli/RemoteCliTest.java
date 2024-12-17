package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteProcessTool;

/**
 * Basically the same test as {@link MinionDeployTest} but using the CLI.
 */
@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteCliTest extends BaseMinionCliTest {

    @Test
    void testRemoteCli(BHive local, MasterRootResource master, CommonRootResource common, RemoteService remote, @TempDir Path tmp,
            @AuthPack String auth) throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        // deploy and activate on remote master
        assertTrue(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--install");
        assertFalse(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());

        tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list");

        // test uninstall and re-install once
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--uninstall");
        assertTrue(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--install");
        assertFalse(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());

        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--activate");
        assertEquals(instance.getTag(), master.getNamedMaster("demo").getInstanceState(id).activeTag);

        // run/control processes on the remote
        tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--application=app", "--start");

        InstanceStatusDto status = master.getNamedMaster("demo").getStatus(id);
        System.out.println(status);
        assertTrue(status.isAppRunningOrScheduled("app"));

        tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--list");

        // give the script a bit to write output
        Threads.sleep(200);

        tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--application=app", "--stop");

        status = master.getNamedMaster("demo").getStatus(id);
        System.out.println(status);
        assertFalse(status.isAppRunningOrScheduled("app"));
    }
}

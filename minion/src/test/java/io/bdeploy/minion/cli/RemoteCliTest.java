package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.minion.deploy.MinionDeployTest;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;

/**
 * Basically the same test as {@link MinionDeployTest} but using the CLI.
 */
@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class RemoteCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testRemoteCli(BHive local, MasterRootResource master, CleanupResource cr, RemoteService remote, @TempDir Path tmp,
            ActivityReporter reporter, MinionRoot mr, @AuthPack String auth) throws IOException, InterruptedException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp);

        String uuid = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 5: deploy, activate on remote master */
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        tools.getTool(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--manifest=" + instance, "--install").run();
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());

        tools.getTool(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo", "--list")
                .run();

        // test uninstall, re-install once
        tools.getTool(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--manifest=" + instance, "--uninstall").run();
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        tools.getTool(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--manifest=" + instance, "--install").run();
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());

        tools.getTool(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--manifest=" + instance, "--activate").run();
        assertTrue(master.getNamedMaster("demo").getActiveDeployments().containsKey(uuid));
        assertTrue(master.getNamedMaster("demo").getActiveDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME).containsKey(uuid));

        /* STEP 6: run/control processes on the remote */
        tools.getTool(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--uuid=" + uuid, "--application=app", "--start").run();

        InstanceStatusDto status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertTrue(status.isAppRunningOrScheduled("app"));

        tools.getTool(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--uuid=" + uuid, "--status").run();

        // give the script a bit to write output
        Thread.sleep(200);

        tools.getTool(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--target=demo",
                "--uuid=" + uuid, "--application=app", "--stop").run();

        status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertFalse(status.isAppRunningOrScheduled("app"));
    }
}

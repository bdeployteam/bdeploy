package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ServerErrorException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteDeploymentCliTest extends BaseMinionCliTest {

    @Test
    void testUninstallActiveInstanceReturnsReadableMessage(BHive local, MasterRootResource master, CommonRootResource common,
            RemoteService remote, @TempDir Path tmp) throws IOException {
        // Arrange
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);
        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);
        assertTrue(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--activate");

        // Act & Assert
        Exception result = assertThrows(ClientErrorException.class, () -> {
            remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                    "--uninstall");
        });
        assertEquals("HTTP 417 Cannot uninstall active version", result.getMessage());
    }

    @Test
    void testActivationBeforeInstallationReturnsReadableMessage(BHive local, MasterRootResource master, CommonRootResource common,
            RemoteService remote, @TempDir Path tmp) throws IOException {
        // Arrange
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);
        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);
        assertTrue(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());

        // Act & Assert
        Exception result = assertThrows(ServerErrorException.class, () -> {
            remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                    "--activate");
        });
        assertTrue(result.getMessage().contains("HTTP 502 Cannot activate on master; HTTP 500 Activation failed because key aaa-bbb-ccc/master:1 is not installed."));
    }
}

package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherCli;
import io.bdeploy.launcher.cli.LauncherTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.pcu.ProcessController;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class MinionDeployTest {

    @RegisterExtension
    TestCliTool launcher = new TestCliTool(new LauncherCli());

    @Test
    @SlowTest
    void testRemoteDeploy(BHive local, MasterRootResource master, CleanupResource cr, RemoteService remote, @TempDir Path tmp,
            ActivityReporter reporter, MinionRoot mr) throws IOException, InterruptedException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp);

        String uuid = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 5: deploy, activate on remote master */
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        master.getNamedMaster("demo").install(instance);
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());

        // test uninstall, re-install once
        master.getNamedMaster("demo").remove(instance);
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        master.getNamedMaster("demo").install(instance);
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());

        master.getNamedMaster("demo").activate(instance);
        assertTrue(master.getNamedMaster("demo").getActiveDeployments().containsKey(uuid));
        assertTrue(master.getNamedMaster("demo").getActiveDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME).containsKey(uuid));

        /* STEP 6: run/control processes on the remote */
        master.getNamedMaster("demo").start(uuid, "app");

        InstanceStatusDto status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertTrue(status.isAppRunningOrScheduled("app"));

        // give the script a bit to write output
        Thread.sleep(200);

        master.getNamedMaster("demo").stop(uuid, "app");
        status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertFalse(status.isAppRunningOrScheduled("app"));

        // instance has a single server application, fetch its id and query output
        InstanceManifest imf = InstanceManifest.of(local, instance);
        Key nodeKey = imf.getInstanceNodeManifests().get("master");
        InstanceNodeManifest inmf = InstanceNodeManifest.of(local, nodeKey);
        String appId = inmf.getConfiguration().applications.get(0).uid;

        InstanceDirectory id = master.getNamedMaster("demo").getOutputEntry(uuid, instance.getTag(), appId);
        assertNotNull(id);
        assertEquals(1, id.entries.size());

        InstanceDirectoryEntry ide = id.entries.get(0);
        assertEquals(SpecialDirectory.RUNTIME, ide.root);
        assertEquals(appId + "/" + ProcessController.OUT_TXT, ide.path);

        // output may contain additional output, e.g. "demo-linux_1.0.0.1234/launch.sh: line 3: 29390 Terminated              sleep $1"
        String expectedText = "Hello script" + System.lineSeparator();
        EntryChunk output = master.getNamedMaster("demo").getEntryContent(id.minion, ide, 0, expectedText.length());
        assertNotNull(output);
        String content = new String(output.content, StandardCharsets.UTF_8);
        assertEquals(expectedText, content);

        /* STEP 7: generate client .bdeploy file and feed launcher */
        ClickAndStartDescriptor cdesc = new ClickAndStartDescriptor();
        cdesc.applicationId = "client";
        cdesc.groupId = "demo";
        cdesc.instanceId = uuid;
        cdesc.host = new RemoteService(remote.getUri(), master.getNamedMaster("demo").generateWeakToken("client"));

        Path bdeployFile = tmp.resolve("client.bdeploy");
        Files.write(bdeployFile, StorageHelper.toRawBytes(cdesc));

        /* STEP 8: launcher client, assert that the script does some sleeping... */
        launcher.getTool(LauncherTool.class, "--cacheDir=" + tmp.resolve("launcher"), "--launch=" + bdeployFile).run();

        // if we reach here, launching succeeded. unfortunately no better way to check right now.

        /* STEP 9: uninstall and cleanup */
        List<CleanupGroup> nothingToDo = cr.calculate();
        assertEquals(1, nothingToDo.size());
        assertEquals(0, nothingToDo.get(0).actions.size());

        try (RemoteBHive rbh = RemoteBHive.forService(remote, "demo", reporter)) {
            rbh.removeManifest(instance); // remove top level instance.
        }

        List<CleanupGroup> groups = cr.calculate();
        assertEquals(1, groups.size());
        assertEquals("master", groups.get(0).minion);

        // 1 instance node manifest, 1 application manifest, 1 dependent manifest
        // 1 instance version dir (not uninstalled before)
        // 1 instance data dir (last version removed), 2 stale pool dirs (application, dependent).
        assertEquals(7, groups.get(0).actions.size());

        // now actually do it.
        cr.perform(groups);

        // no manifests should be left now
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> inventory = rbh.getManifestInventory();
            assertTrue(inventory.isEmpty());
        }

        // only the deployment dir itself and the pool directory is allowed to be alive, no other path may exist after cleanup.
        assertEquals(0,
                Files.walk(mr.getDeploymentDir())
                        .filter(p -> !p.equals(mr.getDeploymentDir())
                                && !p.equals(mr.getDeploymentDir().resolve(SpecialDirectory.MANIFEST_POOL.getDirName())))
                        .count());
    }

    @Test
    void testImportedDeploy(BHive local, MasterRootResource master, SlaveCleanupResource scr, RemoteService remote,
            @TempDir Path tmp, ActivityReporter reporter, MinionRoot mr) throws IOException, InterruptedException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp);
        InstanceManifest im1 = InstanceManifest.of(local, instance);

        /* STEP 1: export and re-import instance */
        Path tmpZip = tmp.resolve("export.zip");
        InstanceImportExportHelper.exportTo(tmpZip, local, im1);
        Manifest.Key importedInstance = InstanceImportExportHelper.importFrom(tmpZip, local, UuidHelper.randomId(), null);

        // check application UIDs
        InstanceManifest im2 = InstanceManifest.of(local, importedInstance);

        InstanceNodeManifest master1 = InstanceNodeManifest.of(local,
                im1.getInstanceNodeManifests().get(Minion.DEFAULT_MASTER_NAME));
        InstanceNodeManifest master2 = InstanceNodeManifest.of(local,
                im2.getInstanceNodeManifests().get(Minion.DEFAULT_MASTER_NAME));

        // IDs may NEVER match.
        assertEquals(master1.getConfiguration().applications.get(0).name, master2.getConfiguration().applications.get(0).name);
        assertNotEquals(master1.getConfiguration().applications.get(0).uid, master2.getConfiguration().applications.get(0).uid);

        // test re-import for same instance (new version) - applications UID must stay the same.
        Manifest.Key importedVersion = InstanceImportExportHelper.importFrom(tmpZip, local, im1.getConfiguration().uuid, null);
        assertEquals("2", importedVersion.getTag()); // new version
        assertEquals(instance.getName(), importedVersion.getName());

        InstanceManifest im3 = InstanceManifest.of(local, importedVersion);
        InstanceNodeManifest master3 = InstanceNodeManifest.of(local,
                im3.getInstanceNodeManifests().get(Minion.DEFAULT_MASTER_NAME));

        // IDs MUST match.
        assertEquals(master1.getConfiguration().applications.get(0).name, master3.getConfiguration().applications.get(0).name);
        assertEquals(master1.getConfiguration().applications.get(0).uid, master3.getConfiguration().applications.get(0).uid);

        /* STEP 2: push to remote */
        local.execute(new PushOperation().setRemote(remote).setHiveName("demo").addManifest(importedInstance));

        String uuid = local.execute(new ManifestLoadOperation().setManifest(importedInstance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 5: deploy, activate on remote master */
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertTrue(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        master.getNamedMaster("demo").install(importedInstance);
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfInstance(uuid).isEmpty());
        assertFalse(master.getNamedMaster("demo").getAvailableDeploymentsOfMinion(Minion.DEFAULT_MASTER_NAME, uuid).isEmpty());
        master.getNamedMaster("demo").activate(importedInstance);
    }

}

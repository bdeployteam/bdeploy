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
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
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
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherCli;
import io.bdeploy.launcher.cli.LauncherTool;
import io.bdeploy.launcher.cli.UninstallerTool;
import io.bdeploy.logging.process.RollingStreamGobbler;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.CleanupResource;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class MinionDeployTest {

    @RegisterExtension
    private final TestCliTool launcher = new TestCliTool(new LauncherCli());

    @SlowTest
    @Test
    void testRemoteDeploy(BHive local, MasterRootResource master, CommonRootResource common, CleanupResource cr,
            RemoteService remote, @TempDir Path tmp, ActivityReporter reporter, MinionRoot mr)
            throws IOException, InterruptedException {
        SortedMap<Key, ObjectId> inventoryStart = null;
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            inventoryStart = TestFactory.getFilteredManifests(rbh.getManifestInventory());
        }

        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String instanceId = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 5: deploy, activate on remote master */
        assertTrue(master.getNamedMaster("demo").getInstanceState(instanceId).installedTags.isEmpty());
        master.getNamedMaster("demo").install(instance);
        assertFalse(master.getNamedMaster("demo").getInstanceState(instanceId).installedTags.isEmpty());

        // test uninstall, re-install once
        master.getNamedMaster("demo").uninstall(instance);
        assertTrue(master.getNamedMaster("demo").getInstanceState(instanceId).installedTags.isEmpty());
        master.getNamedMaster("demo").install(instance);
        assertFalse(master.getNamedMaster("demo").getInstanceState(instanceId).installedTags.isEmpty());

        master.getNamedMaster("demo").activate(instance);
        assertEquals(instance.getTag(), master.getNamedMaster("demo").getInstanceState(instanceId).activeTag);

        // check the deployment info file for correct content
        Path infoFile = mr.getDeploymentDir().resolve(instanceId).resolve(InstanceDeploymentInformationApi.FILE_NAME);
        assertTrue(Files.exists(infoFile));

        InstanceDeploymentInformationApi info = StorageHelper.fromPath(infoFile, InstanceDeploymentInformationApi.class);
        assertEquals(instance.getTag(), info.activeInstanceVersion);
        assertEquals(instanceId, info.instance.uuid);

        /* STEP 6: run/control processes on the remote */
        master.getNamedMaster("demo").start(instanceId, List.of("app"));

        InstanceStatusDto status;
        do {
            Thread.sleep(10);
            status = master.getNamedMaster("demo").getStatus(instanceId);
        } while (status.getAppStatus().get("app").processState != ProcessState.RUNNING);

        assertTrue(status.isAppRunningOrScheduled("app"));
        assertEquals(ProcessState.RUNNING, status.node2Applications.get("master").getStatus("app").processState);

        ProcessDetailDto details = master.getNamedMaster("demo").getProcessDetailsFromNode(instanceId, "app", "master");
        assertNotNull(details);
        assertEquals(ProcessState.RUNNING, details.status.processState);

        // give the script a bit to write output. we need a "rather long" timeout to give
        // the log gobbler a chance to flush everything through the pipes.
        Threads.sleep(200);

        master.getNamedMaster("demo").stop(instanceId, List.of("app"));
        status = master.getNamedMaster("demo").getStatus(instanceId);
        System.out.println(status);
        assertFalse(status.isAppRunningOrScheduled("app"));

        // instance has a single server application, fetch its id and query output
        InstanceManifest imf = InstanceManifest.of(local, instance);
        Key nodeKey = imf.getInstanceNodeManifestKeys().get("master");
        InstanceNodeManifest inmf = InstanceNodeManifest.of(local, nodeKey);
        String appId = inmf.getConfiguration().applications.get(0).id;

        RemoteDirectory id = master.getNamedMaster("demo").getOutputEntry(instanceId, instance.getTag(), appId);
        assertNotNull(id);
        assertEquals(1, id.entries.size());

        RemoteDirectoryEntry ide = id.entries.get(0);
        assertEquals(SpecialDirectory.RUNTIME, ide.root);
        assertEquals(appId + "/" + RollingStreamGobbler.OUT_TXT, ide.path);

        // output may contain additional output, e.g. "demo-linux_1.0.0.1234/launch.sh: line 3: 29390 Terminated              sleep $1"
        Pattern expectedHeader = Pattern.compile(".* | --- Starting output capture .*");
        Pattern expected = Pattern.compile(".* | Hello script");
        EntryChunk output = master.getNamedMaster("demo").getEntryContent(id.minion, ide, 0, ide.size);
        assertNotNull(output);
        String content = new String(output.content, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n|\\r");
        assertTrue(lines.length > 1);
        assertTrue(expectedHeader.matcher(lines[0]).find());
        assertTrue(expected.matcher(lines[1]).find());

        /* STEP 7: generate client .bdeploy file and feed launcher */
        ClickAndStartDescriptor cdesc = new ClickAndStartDescriptor();
        cdesc.applicationId = "client";
        cdesc.groupId = "demo";
        cdesc.instanceId = instanceId;
        cdesc.host = new RemoteService(remote.getUri(), master.getNamedMaster("demo").generateWeakToken("Test"));

        Path bdeployFile = tmp.resolve("client.bdeploy");
        Files.write(bdeployFile, StorageHelper.toRawBytes(cdesc));

        /* STEP 8: launcher client, assert that the script does some sleeping... */
        launcher.execute(LauncherTool.class, "--homeDir=" + tmp.resolve("launcher"), "--launch=" + bdeployFile, "--unattended",
                "--consoleLog");
        launcher.execute(UninstallerTool.class, "--homeDir=" + tmp.resolve("launcher"), "--app=client", "--yes");

        // if we reach here, launching succeeded. unfortunately no better way to check right now.

        /* STEP 9: uninstall and cleanup */
        List<CleanupGroup> nothingToDo = cr.calculate();
        assertEquals(2, nothingToDo.size());
        assertEquals(0, nothingToDo.get(0).actions.size());
        assertEquals(0, nothingToDo.get(1).actions.size());

        try (RemoteBHive rbh = RemoteBHive.forService(remote, "demo", reporter)) {
            rbh.removeManifest(instance); // remove top level instance.
        }

        List<CleanupGroup> groups = cr.calculate();
        assertEquals(2, groups.size());
        assertEquals("demo", groups.get(0).instanceGroup);
        assertEquals(5, groups.get(0).actions.size()); //TODO figure out why this assertion fails locally, but succeeds on jenkins

        assertEquals("master", groups.get(1).minion);
        // 1 instance node manifest, 1 application manifest, 1 dependent manifest
        // 1 instance version dir (not uninstalled before)
        // 1 instance data dir (last version removed), 2 stale pool dirs (application, dependent).
        // 4 meta manifests (state of instance & runtime history).
        assertEquals(11, groups.get(1).actions.size());

        // now actually do it.
        cr.perform(groups);

        // All created manifests should be deleted
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> inventory = TestFactory.getFilteredManifests(rbh.getManifestInventory());
            inventory.keySet().removeAll(inventoryStart.keySet());
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
    void testImportedDeploy(BHive local, MasterRootResource master, CommonRootResource common, RemoteService remote,
            @TempDir Path tmp, MinionRoot mr) throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);
        InstanceManifest im1 = InstanceManifest.of(local, instance);

        /* STEP 1: export and re-import instance */
        Path tmpZip = tmp.resolve("export.zip");
        InstanceImportExportHelper.exportTo(tmpZip, local, im1);
        Manifest.Key importedInstance = InstanceImportExportHelper.importFrom(tmpZip, local, UuidHelper.randomId(),
                mr.getNodeManager().getAllNodes(), null);

        // check application UIDs
        InstanceManifest im2 = InstanceManifest.of(local, importedInstance);

        InstanceNodeManifest master1 = InstanceNodeManifest.of(local, im1.getInstanceNodeManifestKeys().get(Minion.DEFAULT_NAME));
        InstanceNodeManifest master2 = InstanceNodeManifest.of(local, im2.getInstanceNodeManifestKeys().get(Minion.DEFAULT_NAME));

        // IDs may NEVER match.
        assertEquals(master1.getConfiguration().applications.get(0).name, master2.getConfiguration().applications.get(0).name);
        assertNotEquals(master1.getConfiguration().applications.get(0).id, master2.getConfiguration().applications.get(0).id);

        // test re-import for same instance (new version) - applications UID must stay the same.
        Manifest.Key importedVersion = InstanceImportExportHelper.importFrom(tmpZip, local, im1.getConfiguration().id,
                mr.getNodeManager().getAllNodes(), null);
        assertEquals("2", importedVersion.getTag()); // new version
        assertEquals(instance.getName(), importedVersion.getName());

        InstanceManifest im3 = InstanceManifest.of(local, importedVersion);
        InstanceNodeManifest master3 = InstanceNodeManifest.of(local, im3.getInstanceNodeManifestKeys().get(Minion.DEFAULT_NAME));

        // IDs MUST match.
        assertEquals(master1.getConfiguration().applications.get(0).name, master3.getConfiguration().applications.get(0).name);
        assertEquals(master1.getConfiguration().applications.get(0).id, master3.getConfiguration().applications.get(0).id);

        /* STEP 2: push to remote */
        local.execute(new PushOperation().setRemote(remote).setHiveName("demo").addManifest(importedInstance));

        String id = local.execute(new ManifestLoadOperation().setManifest(importedInstance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 5: deploy, activate on remote master */
        assertTrue(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());
        master.getNamedMaster("demo").install(importedInstance);
        assertFalse(master.getNamedMaster("demo").getInstanceState(id).installedTags.isEmpty());
        master.getNamedMaster("demo").activate(importedInstance);
    }

}

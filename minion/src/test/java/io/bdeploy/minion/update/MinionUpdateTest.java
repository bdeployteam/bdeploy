package io.bdeploy.minion.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.remote.CommonUpdateResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.minion.cli.MinionServerCli;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.cli.RemoteMasterTool;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
class MinionUpdateTest {

    @RegisterExtension
    TestCliTool cli = new TestCliTool(new MinionServerCli());

    @Test
    void testUpdate(MinionRoot root, CommonUpdateResource resource, RemoteService remote, @TempDir Path tmp, BHive local)
            throws IOException {
        doTestUpdate(root, resource, remote, tmp, local);
    }

    @Tag("CENTRAL")
    @Test
    void testUpdateCentral(MinionRoot root, CommonUpdateResource resource, RemoteService remote, @TempDir Path tmp, BHive local)
            throws IOException {
        doTestUpdate(root, resource, remote, tmp, local);
    }

    @Tag("MANAGED")
    @Test
    void testUpdateManaged(MinionRoot root, CommonUpdateResource resource, RemoteService remote, @TempDir Path tmp, BHive local)
            throws IOException {
        doTestUpdate(root, resource, remote, tmp, local);
    }

    void doTestUpdate(MinionRoot root, CommonUpdateResource resource, RemoteService remote, Path tmp, BHive local)
            throws IOException {
        AtomicBoolean updateTriggered = new AtomicBoolean(false);
        root.setRestartManager((t) -> updateTriggered.set(true));
        root.onStartup(true);

        Path updateSource = TestAppFactory.createDummyApp("minion", tmp);
        Manifest.Key winUpdateKey = new Manifest.Key("bdeploy/snapshot/windows", "2.0.0");
        Manifest.Key linuxUpdateKey = new Manifest.Key("bdeploy/snapshot/linux", "2.0.0");
        Manifest.Key macUpdateKey = new Manifest.Key("bdeploy/snapshot/macos", "2.0.0");

        try (Transaction t = local.getTransactions().begin()) {
            local.execute(new ImportOperation().setManifest(winUpdateKey).setSourcePath(updateSource));
            local.execute(new ImportOperation().setManifest(linuxUpdateKey).setSourcePath(updateSource));
            local.execute(new ImportOperation().setManifest(macUpdateKey).setSourcePath(updateSource));
        }

        local.execute(new PushOperation().addManifest(winUpdateKey).setRemote(remote));
        local.execute(new PushOperation().addManifest(linuxUpdateKey).setRemote(remote));
        local.execute(new PushOperation().addManifest(macUpdateKey).setRemote(remote));
        UpdateHelper.update(remote, Arrays.asList(winUpdateKey, linuxUpdateKey, macUpdateKey), true, null);

        // check that an update was triggered
        assertTrue(updateTriggered.get());
        assertTrue(Files.isDirectory(root.getUpdateDir().resolve("next")));
        assertTrue(Files.isRegularFile(root.getUpdateDir().resolve("next").resolve(ApplicationDescriptor.FILE_NAME)));
    }

    @Test
    void testZippedUpdate(MinionRoot root, CommonUpdateResource resource, RemoteService svc, @TempDir Path tmp,
            @AuthPack String auth) throws IOException, GeneralSecurityException {
        doTestZippedUpdate(root, resource, svc, tmp, auth);
    }

    @Tag("CENTRAL")
    @Test
    void testZippedUpdateCentral(MinionRoot root, CommonUpdateResource resource, RemoteService svc, @TempDir Path tmp,
            @AuthPack String auth) throws IOException, GeneralSecurityException {
        doTestZippedUpdate(root, resource, svc, tmp, auth);
    }

    @Tag("MANAGED")
    @Test
    void testZippedUpdateManaged(MinionRoot root, CommonUpdateResource resource, RemoteService svc, @TempDir Path tmp,
            @AuthPack String auth) throws IOException, GeneralSecurityException {
        doTestZippedUpdate(root, resource, svc, tmp, auth);
    }

    void doTestZippedUpdate(MinionRoot root, CommonUpdateResource resource, RemoteService svc, Path tmp, String auth)
            throws IOException, GeneralSecurityException {
        AtomicBoolean updateTriggered = new AtomicBoolean(false);
        root.setRestartManager((t) -> updateTriggered.set(true));

        // generate test app into a ZIP
        Path zip = tmp.resolve("xxx-2.0.0.zip");

        try (FileSystem zfs = PathHelper.openZip(zip)) {
            Path createDummyApp = TestAppFactory.createDummyApp("xxx-2.0.0", zfs.getPath("/"));
            List<String> lines = new ArrayList<>();
            lines.add("version=2.0.0");
            lines.add("os=LINUX");
            Files.write(createDummyApp.resolve("version.properties"), lines);
        }

        // this will unpack, import, push, and trigger the update
        cli.execute(RemoteMasterTool.class, "--update=" + zip.toString(), "--yes", "--remote=" + svc.getUri(), "--token=" + auth);

        if (OsHelper.getRunningOs() == OperatingSystem.LINUX) {
            assertTrue(updateTriggered.get());
            assertTrue(Files.isDirectory(root.getUpdateDir().resolve("next")));
            assertTrue(Files.isRegularFile(root.getUpdateDir().resolve("next").resolve(ApplicationDescriptor.FILE_NAME)));
        } else {
            assertFalse(updateTriggered.get());
        }
    }

}

package io.bdeploy.minion.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.minion.cli.MinionServerCli;
import io.bdeploy.minion.cli.RemoteMasterTool;
import io.bdeploy.pcu.TestAppFactory;

@ExtendWith(TestMinion.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestHive.class)
public class MinionUpdateTest {

    @RegisterExtension
    TestCliTool cli = new TestCliTool(new MinionServerCli());

    @Test
    void testUpdate(MinionRoot root, MasterRootResource resource, RemoteService remote, @TempDir Path tmp, BHive local)
            throws IOException {
        AtomicBoolean updateTriggered = new AtomicBoolean(false);
        root.setUpdateManager((t) -> updateTriggered.set(true));

        Path updateSource = TestAppFactory.createDummyApp("minion", tmp);
        Manifest.Key updateKey = new Manifest.Key("bdeploy/snapshot/windows", "2.0.0");

        local.execute(new ImportOperation().setManifest(updateKey).setSourcePath(updateSource));
        local.execute(new PushOperation().addManifest(updateKey).setRemote(remote));

        resource.update(updateKey, true);

        // update triggered only on windows, not on linux
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            assertTrue(updateTriggered.get());
            assertTrue(Files.isDirectory(root.getUpdateDir().resolve("next")));
            assertTrue(Files.isRegularFile(root.getUpdateDir().resolve("next").resolve(ApplicationDescriptor.FILE_NAME)));
        } else {
            assertFalse(updateTriggered.get());
        }
    }

    @Test
    void testZippedUpdate(MinionRoot root, MasterRootResource resource, RemoteService svc, @TempDir Path tmp,
            @AuthPack String auth) throws IOException, GeneralSecurityException {
        AtomicBoolean updateTriggered = new AtomicBoolean(false);
        root.setUpdateManager((t) -> updateTriggered.set(true));

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
        cli.getTool(RemoteMasterTool.class, "--update=" + zip.toString(), "--yes", "--remote=" + svc.getUri(), "--token=" + auth)
                .run();

        if (OsHelper.getRunningOs() == OperatingSystem.LINUX) {
            assertTrue(updateTriggered.get());
            assertTrue(Files.isDirectory(root.getUpdateDir().resolve("next")));
            assertTrue(Files.isRegularFile(root.getUpdateDir().resolve("next").resolve(ApplicationDescriptor.FILE_NAME)));
        } else {
            assertFalse(updateTriggered.get());
        }
    }

}

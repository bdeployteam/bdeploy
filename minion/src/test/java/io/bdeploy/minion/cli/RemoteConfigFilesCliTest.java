package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteConfigFilesTool;

/**
 * Basically the same test as {@link MinionDeployTest} but using the CLI.
 */
@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteConfigFilesCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp, @AuthPack String auth)
            throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* Check list, add, remove, export, etc. of config files */
        var result = tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--list");

        assertEquals(1, result.size());
        assertEquals("myconfig.json", result.get(0).get("Path"));
        assertEquals("SYNC", result.get(0).get("Status"));

        Path testSource1 = tmp.resolve("test1.txt");
        Files.writeString(testSource1, "Teststring");

        tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--add=test.txt", "--source=" + testSource1);

        result = tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--list");

        assertEquals(2, result.size());
        assertEquals("myconfig.json", result.get(0).get("Path"));
        assertEquals("test.txt", result.get(1).get("Path"));
        assertEquals("ONLY-INSTANCE", result.get(1).get("Status"));

        Path testTarget1 = tmp.resolve("dl1.txt");
        tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--export=test.txt", "--target=" + testTarget1);

        String content = Files.readString(testTarget1);
        assertEquals("Teststring", content);

        Path testSource2 = tmp.resolve("test2.txt");
        Files.writeString(testSource2, "Another Teststring");

        tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--update=test.txt", "--source=" + testSource2);

        Path testTarget2 = tmp.resolve("dl2.txt");
        tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--export=test.txt", "--target=" + testTarget2);

        content = Files.readString(testTarget2);
        assertEquals("Another Teststring", content);

        tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--delete=test.txt");

        result = tools.execute(RemoteConfigFilesTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--list");

        assertEquals(1, result.size());
        assertEquals("myconfig.json", result.get(0).get("Path"));
        assertEquals("SYNC", result.get(0).get("Status"));
    }
}

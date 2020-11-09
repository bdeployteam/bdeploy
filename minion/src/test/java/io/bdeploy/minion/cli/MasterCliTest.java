package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.bhive.cli.TokenTool;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.security.AuthPackAccessor;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.LocalLoginTool;
import io.bdeploy.minion.BCX509Helper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.shutdown.RemoteShutdown;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.cli.RemoteUserTool;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class MasterCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @RegisterExtension
    TestCliTool hiveTools = new TestCliTool(new BHiveCli());

    @Test
    void testMasterCli(@TempDir Path tmp, ActivityReporter reporter) throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path root = tmp.resolve("root");
        Path storage = tmp.resolve("storage");
        tools.execute(InitTool.class, "--root=" + root, "--hostname=localhost", "--dist=ignore", "--mode=standalone",
                "--initUser=test", "--initPassword=test", "--port=" + port);
        tools.execute(StorageTool.class, "--root=" + root, "--add=" + storage.toString());

        Path ks;
        char[] pp;
        String pack;

        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            assertEquals(2, mr.getStorageLocations().size());
            assertTrue(mr.getStorageLocations().contains(storage));
            assertTrue(mr.getUsers().getAllNames().contains("test"));
            assertNotNull(mr.getUsers().authenticate("test", "test"));
            assertNull(mr.getUsers().authenticate("test", "wrong"));

            ks = mr.getState().keystorePath;
            pp = mr.getState().keystorePass;
            pack = AuthPackAccessor.getAuthPack(mr.getMinions().getRemote(Minion.DEFAULT_NAME).getKeyStore());
        }

        Path tmpStore = tmp.resolve("pubstore");

        hiveTools.execute(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp), "--load",
                "--pack=" + pack);

        String[] token = hiveTools.execute(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp),
                "--dump");

        hiveTools.execute(TokenTool.class, "--keystore=" + ks.toString(), "--passphrase=" + new String(pp), "--check",
                "--token=" + token[0]);

        tools.execute(StorageTool.class, "--root=" + root, "--remove=" + storage.toString());

        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            assertEquals(1, mr.getStorageLocations().size());
            assertFalse(mr.getStorageLocations().contains(storage));
        }

        tools.execute(CleanupTool.class, "--root=" + root, "--setSchedule=1 0 0 * * ?");
        String[] output = tools.execute(CleanupTool.class, "--root=" + root);
        assertTrue(output[1].contains("1 0 0 * * ?"));

        // test certificate update with the same certificate
        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            Path newKs = tmp.resolve("test.ks");
            char[] passphrase = new char[] { 'a' };
            BCX509Helper.createKeyStore(newKs, passphrase);

            Path pem = tmp.resolve("test.pem");
            BCX509Helper.exportPrivateCertificateAsPem(newKs, passphrase, pem);

            tools.execute(CertUpdateTool.class, "--root=" + root, "--update=" + pem, "--yes");
        }

        tools.execute(ConfigTool.class, "--root=" + root, "--update=localhost");

        String shutdown = UuidHelper.randomId();

        RemoteService self;
        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            self = mr.getSelf();
        }

        Thread master = new Thread(() -> {
            try {
                tools.execute(StartTool.class, "--root=" + root, "--shutdownToken=" + shutdown);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "UnitTestMaster");
        master.start();

        while (true) {
            try {
                ResourceProvider.getResource(self, PublicRootResource.class, null).getVersion();
                break;
            } catch (Exception e) {
                // need to retry, server not yet up.
            }
        }

        try {
            // set user home to tmp to avoid influencing host.
            System.setProperty("user.home", tmp.toString());

            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(2, output.length);

            tools.execute(LocalLoginTool.class, "--add=MyServer", "--remote=https://localhost:" + port + "/api", "--user=test",
                    "--password=test");

            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(3, output.length);
            assertTrue(output[1].contains("MyServer"));

            tools.execute(LocalLoginTool.class, "--add=MyOtherServer", "--remote=https://localhost:" + port + "/api",
                    "--user=test", "--password=test");
            tools.execute(LocalLoginTool.class, "--use=MyServer");
            tools.execute(LocalLoginTool.class, "--remove=MyServer");

            // no current tool now.
            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(3, output.length);
            assertFalse(output[1].contains("*"));

            // change current
            tools.execute(LocalLoginTool.class, "--use=MyOtherServer");

            // use the login, test the user tool
            ToolBase.setTestModeForLLM(false); // disable to allow local login manager.
            tools.execute(RemoteUserTool.class, "--add=user1", "--password=user1");
            tools.execute(RemoteUserTool.class, "--update=user1", "--admin");
            tools.execute(RemoteUserTool.class, "--update=user1", "--permission=WRITE", "--scope=IG");
            output = tools.execute(RemoteUserTool.class, "--list");
            assertTrue(Arrays.stream(output).anyMatch(s -> s.contains("user1")));
            ToolBase.setTestModeForLLM(true);

            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(3, output.length);
            assertTrue(output[1].contains("*"));

            tools.execute(LocalLoginTool.class, "--remove=MyOtherServer");
            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(2, output.length);
        } finally {
            ResourceProvider.getResource(self, RemoteShutdown.class, null).shutdown(shutdown);
            master.join();
        }

        tools.execute(ConfigTool.class, "--root=" + root, "--mode=MANAGED");
        assertThrows(UnsupportedOperationException.class, () -> {
            tools.execute(ConfigTool.class, "--root=" + root, "--mode=CENTRAL");
        });
    }

}

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.bhive.cli.TokenTool;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.cli.ToolBase;
import io.bdeploy.common.security.AuthPackAccessor;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.BCX509Helper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.shutdown.RemoteShutdown;
import io.bdeploy.ui.cli.RemoteUserTool;

@ExtendWith(TestActivityReporter.class)
class MasterCliTest {

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
                "--initUser=test", "--initPassword=testtesttest", "--port=" + port);
        tools.execute(StorageTool.class, "--root=" + root, "--add=" + storage.toString());

        Path ks;
        char[] pp;
        String pack;

        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            assertEquals(2, mr.getStorageLocations().size());
            assertTrue(mr.getStorageLocations().contains(storage));
            assertTrue(mr.getUsers().getAllNames().contains("test"));
            assertNotNull(mr.getUsers().authenticate("test", "testtesttest"));
            assertNull(mr.getUsers().authenticate("test", "wrong"));

            ks = mr.getState().keystorePath;
            pp = mr.getState().keystorePass;

            pack = AuthPackAccessor
                    .getAuthPack(new MinionManifest(mr.getHive()).read().getMinion(mr.getState().self).remote.getKeyStore());
        }

        Path tmpStore = tmp.resolve("pubstore");

        hiveTools.execute(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp), "--load",
                "--pack=" + pack);

        String[] token = hiveTools
                .execute(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp), "--dump")
                .getRawOutput();

        hiveTools.execute(TokenTool.class, "--keystore=" + ks.toString(), "--passphrase=" + new String(pp), "--check",
                "--token=" + token[0]);

        tools.execute(StorageTool.class, "--root=" + root, "--remove=" + storage.toString());

        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            assertEquals(1, mr.getStorageLocations().size());
            assertFalse(mr.getStorageLocations().contains(storage));
        }

        tools.execute(CleanupTool.class, "--root=" + root, "--setSchedule=1 0 0 * * ?");
        var output = tools.execute(CleanupTool.class, "--root=" + root);
        assertEquals("1 0 0 * * ?", output.get(0).get("Schedule"));

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
            self = new MinionManifest(mr.getHive()).read().getMinion(mr.getState().self).remote;
        }

        Thread master = new Thread(() -> {
            try {
                tools.execute(StartTool.class, "--root=" + root, "--shutdownToken=" + shutdown, "--consoleLog");
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
            assertEquals(0, output.size());

            tools.execute(LocalLoginTool.class, "--add=MyServer", "--remote=https://localhost:" + port + "/api", "--user=test",
                    "--password=testtesttest");

            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(1, output.size());
            assertEquals("MyServer", output.get(0).get("Name"));
            assertTrue(output.get(0).get("Uri").contains("localhost"));

            tools.execute(LocalLoginTool.class, "--add=MyOtherServer", "--remote=https://localhost:" + port + "/api",
                    "--user=test", "--password=testtesttest");
            tools.execute(LocalLoginTool.class, "--use=MyServer");
            tools.execute(LocalLoginTool.class, "--remove=MyServer");

            // no current tool now.
            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(1, output.size());
            assertEquals("MyOtherServer", output.get(0).get("Name"));
            assertEquals("", output.get(0).get("Active"));

            // change current
            tools.execute(LocalLoginTool.class, "--use=MyOtherServer");

            // use the login, test the user tool
            ToolBase.setTestModeForLLM(false); // disable to allow local login manager.
            tools.execute(RemoteUserTool.class, "--add=user1", "--password=user1user1user1");
            tools.execute(RemoteUserTool.class, "--update=user1", "--admin");
            tools.execute(RemoteUserTool.class, "--update=user1", "--permission=WRITE", "--scope=IG");
            output = tools.execute(RemoteUserTool.class, "--list");
            assertEquals("test", output.get(0).get("Username"));
            assertEquals("user1", output.get(1).get("Username"));
            ToolBase.setTestModeForLLM(true);

            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(1, output.size());
            assertEquals("*", output.get(0).get("Active"));

            tools.execute(LocalLoginTool.class, "--remove=MyOtherServer");
            output = tools.execute(LocalLoginTool.class, "--list");
            assertEquals(0, output.size());
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

package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.cli.TokenTool;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.AuthPackAccessor;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class MasterCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testMasterCli(@TempDir Path tmp, ActivityReporter reporter) throws IOException {
        Path root = tmp.resolve("root");
        Path storage = tmp.resolve("storage");
        tools.getTool(InitTool.class, "--root=" + root, "--hostname=localhost", "--dist=ignore", "--mode=standalone",
                "--initUser=test", "--initPassword=test").run();
        tools.getTool(StorageTool.class, "--root=" + root, "--add=" + storage.toString()).run();

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

        tools.getTool(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp), "--load",
                "--pack=" + pack).run();

        String token;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            TokenTool tool = tools.getTool(TokenTool.class, "--keystore=" + tmpStore.toString(), "--passphrase=" + new String(pp),
                    "--dump");
            tool.setOutput(new PrintStream(os));
            tool.run();

            token = os.toString();
        }

        tools.getTool(TokenTool.class, "--keystore=" + ks.toString(), "--passphrase=" + new String(pp), "--check",
                "--token=" + token).run();

        tools.getTool(StorageTool.class, "--root=" + root, "--remove=" + storage.toString()).run();

        try (MinionRoot mr = new MinionRoot(root, reporter)) {
            assertEquals(1, mr.getStorageLocations().size());
            assertFalse(mr.getStorageLocations().contains(storage));
        }
    }

}

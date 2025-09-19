package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
public class InitCliTest extends BaseMinionCliTest {

    private static final String TEST_FOLDER = "root";

    @AfterEach
    public void cleanup(@TempDir Path tmp) throws IOException {
        Files.deleteIfExists(tmp.resolve(TEST_FOLDER));
    }

    @Test
    void testInitRequiresStrongEnoughPassword(@TempDir Path tmp) throws IOException {
        TestCliTool.StructuredOutput result;
        Path root = tmp.resolve(TEST_FOLDER);
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        result = tools.execute(InitTool.class, "--root=" + root, "--hostname=localhost", "--dist=ignore", "--mode=standalone",
                "--initUser=test", "--initPassword=abc", "--port=" + port);
        assertEquals("Password too short. Minimum: 12 characters.", result.get(0).get("message"));
        assertFalse(Files.exists(root));

        result = tools.execute(InitTool.class, "--root=" + root, "--hostname=localhost", "--dist=ignore", "--mode=standalone",
                "--initUser=test", "--initPassword=adminadminadmin", "--port=" + port);
        assertEquals("Success", result.get(0).get("message"));
        assertTrue(Files.exists(root));
    }
}

package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteNodeTool;
import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteNodeCliTest extends BaseMinionCliTest {

    private final static String MASTER_FILE_NAME = "test4.json";

    @BeforeEach
    public void setup() throws IOException {
        Files.deleteIfExists(Paths.get(MASTER_FILE_NAME));
    }

    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(Paths.get(MASTER_FILE_NAME));
    }

    @Test
    void testMultiNodeManagement(RemoteService remote)
            throws IOException {
        StructuredOutput result;
        Map<String, TestCliTool.StructuredOutputRow> nodesMap;
        TestCliTool.StructuredOutputRow masterRow;
        TestCliTool.StructuredOutputRow multinodeRow;
        Exception thrownException;

        // check default state
        nodesMap = doRemoteAndIndexOutputOn("Name", remote, RemoteNodeTool.class, "--list");
        assertEquals(1, nodesMap.size());
        assertEquals("SERVER", nodesMap.get("master").get("Type"));
        assertEquals("*", nodesMap.get("master").get("Online"));
        assertEquals("", nodesMap.get("master").get("Status"));

        // Add Multi Node and check the list
        result = remote(remote, RemoteNodeTool.class, "--addMulti=Test", "--operatingSystem=WINDOWS");
        assertEquals("Success", result.get(0).get("message"));

        nodesMap = doRemoteAndIndexOutputOn("Name", remote, RemoteNodeTool.class, "--list");
        assertEquals(2, nodesMap.size());
        masterRow = nodesMap.get("master");
        assertEquals("SERVER", masterRow.get("Type"));
        assertEquals("*", masterRow.get("Online"));
        assertEquals("", masterRow.get("Status"));
        assertNotEquals("", masterRow.get("Os"));

        multinodeRow = nodesMap.get("Test");
        assertEquals("MULTI", multinodeRow.get("Type"));
        assertEquals("", multinodeRow.get("Online"));
        assertEquals("WINDOWS", multinodeRow.get("Os"));
        assertEquals("Waiting for runtime nodes...", multinodeRow.get("Status"));

        // Checking validations
        // node name is unique
        thrownException = assertThrows(InternalServerErrorException.class,
                () -> remote(remote, RemoteNodeTool.class, "--addMulti=Test", "--operatingSystem=WINDOWS"));
        assertEquals("HTTP 500 Minion with the same name already exists.", thrownException.getMessage());
        // operating system is required
        thrownException = assertThrows(InternalServerErrorException.class,
                () -> remote(remote, RemoteNodeTool.class, "--addMulti=Test2"));
        assertEquals("HTTP 500 To add a multi-node, the operating system must be entered and must be supported.", thrownException.getMessage());
        // AIX is deprecated is not allowed
        thrownException = assertThrows(InternalServerErrorException.class,
                () -> remote(remote, RemoteNodeTool.class, "--addMulti=Test3", "--operatingSystem=AIX"));
        assertEquals("HTTP 500 To add a multi-node, the operating system must be entered and must be supported.", thrownException.getMessage());

        // Add one more and check master file
        result = remote(remote, RemoteNodeTool.class, "--addMulti=Test4", "--operatingSystem=WINDOWS");
        assertEquals("Success", result.get(0).get("message"));

        result = remote(remote, RemoteNodeTool.class, "--name=Test4", "--masterFile=" + MASTER_FILE_NAME);
        assertEquals("Multi-node Test4 master file has been successfully written to " + MASTER_FILE_NAME + ".",
                result.get(0).get("message"));

        JsonNode rootNode = new ObjectMapper().readTree(new File(MASTER_FILE_NAME));
        assertEquals("Test4", rootNode.get("name").asText());
        assertEquals(masterRow.get("Uri"), rootNode.get("master").get("uri").asText());
        assertNotEquals("", rootNode.get("master").get("authPack").asText());

        // Check how the master file generation doesn't work
        // missing name
        thrownException = assertThrows(IllegalArgumentException.class,
                () -> remote(remote, RemoteNodeTool.class, "--masterFile=some-other-file.json"));
        assertEquals("ERROR: Cannot generate the master file without the node --name", thrownException.getMessage());
        // missing path
        thrownException = assertThrows(IllegalArgumentException.class, () -> remote(remote, RemoteNodeTool.class, "--name=Test"));
        assertEquals("ERROR: Missing --masterFile", thrownException.getMessage());
        // writing in an existing file
        thrownException = assertThrows(IllegalArgumentException.class,
                () -> remote(remote, RemoteNodeTool.class, "--name=Test", "--masterFile=" + MASTER_FILE_NAME));
        assertEquals("Target file already exists: " + MASTER_FILE_NAME + ". Use --force for overwriting existing files.",
                thrownException.getMessage());

        // check that you can use force to write in an existing file
        result = remote(remote, RemoteNodeTool.class, "--name=Test", "--force", "--masterFile=" + MASTER_FILE_NAME);
        assertEquals("Multi-node Test master file has been successfully written to " + MASTER_FILE_NAME + ".",
                result.get(0).get("message"));
        rootNode = new ObjectMapper().readTree(new File(MASTER_FILE_NAME));
        assertEquals("Test", rootNode.get("name").asText());

        // check removing node
        result = remote(remote, RemoteNodeTool.class, "--remove=Test");
        assertEquals("Success", result.get(0).get("message"));

        nodesMap = doRemoteAndIndexOutputOn("Name", remote, RemoteNodeTool.class, "--list");
        assertEquals(2, nodesMap.size());
        masterRow = nodesMap.get("master");
        assertEquals("SERVER", masterRow.get("Type"));
        assertEquals("*", masterRow.get("Online"));
        assertEquals("", masterRow.get("Status"));
        assertNotEquals("", masterRow.get("Os"));

        multinodeRow = nodesMap.get("Test4");
        assertEquals("MULTI", multinodeRow.get("Type"));
        assertEquals("", multinodeRow.get("Online"));
        assertEquals("WINDOWS", multinodeRow.get("Os"));
        assertEquals("Waiting for runtime nodes...", multinodeRow.get("Status"));
    }
}

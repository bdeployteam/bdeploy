package io.bdeploy.minion.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.cli.BaseMinionCliTest;
import io.bdeploy.minion.cli.ProductTool;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteCentralTool;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteNodeTool;
import io.bdeploy.ui.cli.RemoteProductTool;
import io.bdeploy.ui.cli.RemoteRepoTool;
import io.bdeploy.ui.cli.RemoteSystemTool;
import io.bdeploy.ui.dto.NodeAttachDto;

/**
 * This test tests the workflow described in the
 * <a href="https://bdeploy.io/user/tutorials/systemsetupwalkthrough/index.html">system setup tutorial</a> of the official BDeploy
 * documentation.
 */
@ExtendWith(TestActivityReporter.class)
class SystemSetupCliTest extends BaseMinionCliTest {

    @RegisterExtension
    private final TestMinion centralMinion = new TestMinion(MinionMode.CENTRAL);

    @RegisterExtension
    private final TestMinion managedMinion = new TestMinion(MinionMode.MANAGED);

    @RegisterExtension
    private final TestMinion nodeMinion = new TestMinion(MinionMode.NODE);

    private RemoteService central;
    private RemoteService managed;
    private RemoteService node;

    @BeforeEach
    void setupRemoteServices() {
        central = centralMinion.getRemoteService();
        managed = managedMinion.getRemoteService();
        node = nodeMinion.getRemoteService();
    }

    @SlowTest
    @Test
    void testSystemSetupWalkthroughWithSystemTemplate(@TempDir Path tmp, ActivityReporter reporter)
            throws IOException, URISyntaxException {
        setupNodeManagedCentral(tmp, reporter);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        Files.writeString(systemTemplatePath,//
                """
                        name: Test System
                        description: SYSTEM_DESCRIPTION
                        instances:
                          - name: "Test Instance"
                            description: "The Test Instance of the Test System"
                            productId: "io.bdeploy/test"
                            productVersionRegex: "1\\\\..*"
                            templateName: "Default Test Configuration"
                            defaultMappings:
                              - group: "Only Group"
                                node: "master"
                                """);

        // Import the system template
        remote(central, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=SYSTEM_NAME", "--purpose=TEST",
                "--server=" + remote(central, RemoteCentralTool.class, "--instanceGroup=GROUP_NAME", "--list").get(0).get("Name"),
                "--createFrom=" + systemTemplatePath);

        doCheck();
    }

    @SlowTest
    @Test
    void testSystemSetupWalkthroughWithInstanceTemplate(@TempDir Path tmp, ActivityReporter reporter)
            throws IOException, URISyntaxException {
        setupNodeManagedCentral(tmp, reporter);

        // Create a system
        String managedServerName = remote(central, RemoteCentralTool.class, "--instanceGroup=GROUP_NAME", "--list").get(0)
                .get("Name");
        remote(central, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=SYSTEM_NAME",
                "--description=SYSTEM_DESCRIPTION", "--server=" + managedServerName);

        // Create the response file
        Path responseFilePath = tmp.resolve("responseFile.yaml");
        remote(central, RemoteProductTool.class, "--repository=SOFTWARE_REPO_NAME", "--createResponseFile=" + responseFilePath,
                "--product=io.bdeploy/test", "--version=1.0.0");

        // Import the response file
        remote(central, RemoteInstanceTool.class, "--create", "--name=INSTANCE_NAME", "--template=" + responseFilePath,
                "--instanceGroup=GROUP_NAME", "--purpose=TEST", "--server=" + managedServerName);

        doCheck();
    }

    private void setupNodeManagedCentral(Path tmp, ActivityReporter reporter) throws IOException, URISyntaxException {
        StructuredOutput output;

        // Create a software repository on central
        output = remote(central, RemoteRepoTool.class, "--list");
        assertEquals(0, output.size());

        remote(central, RemoteRepoTool.class, "--add=SOFTWARE_REPO_NAME", "--description=SOFTWARE_REPO_DESCRIPTION");

        output = remote(central, RemoteRepoTool.class, "--list");
        assertEquals(1, output.size());
        assertEquals("SOFTWARE_REPO_NAME", output.get(0).get("Name"));
        assertEquals("SOFTWARE_REPO_DESCRIPTION", output.get(0).get("Description"));

        // Upload a product to the newly created software repository
        output = remote(central, RemoteProductTool.class, "--list");
        assertEquals(0, output.size());

        createAndUploadProduct(tmp);

        output = remote(central, RemoteProductTool.class, "--list");
        assertEquals(1, output.size());
        assertEquals("SOFTWARE_REPO_NAME", output.get(0).get("Repository"));
        assertEquals("Test Product", output.get(0).get("Name"));
        assertEquals("io.bdeploy/test/product", output.get(0).get("Key"));
        assertEquals("1.0.0", output.get(0).get("Version"));
        assertEquals("1", output.get(0).get("NoOfInstanceTemplates"));
        assertEquals("0", output.get(0).get("NoOfApplicationTemplates"));

        // Create instance group on central and assert its existence and correctness
        output = remote(central, RemoteInstanceGroupTool.class, "--list");
        assertEquals(0, output.size());

        remote(central, RemoteInstanceGroupTool.class, "--create=GROUP_NAME");

        output = remote(central, RemoteInstanceGroupTool.class, "--list");
        assertEquals(1, output.size());
        assertEquals("GROUP_NAME", output.get(0).get("Name"));

        // Create a node ident file
        File nodeIdentFile = tmp.resolve("PATH_TO_NODE_TOKEN.json").toFile();
        NodeAttachDto nodeAttachDto = new NodeAttachDto();
        nodeAttachDto.name = "TestNode";
        nodeAttachDto.sourceMode = MinionMode.NODE;
        nodeAttachDto.remote = node;
        JacksonHelper.getDefaultJsonObjectMapper().writeValue(nodeIdentFile, nodeAttachDto);

        // Connect node to managed and verify the connection
        output = remote(managed, RemoteNodeTool.class, "--list");
        assertEquals(1, output.size());

        remote(managed, RemoteNodeTool.class, "--add=NODE_NAME", "--nodeIdentFile=" + nodeIdentFile);

        output = remote(managed, RemoteNodeTool.class, "--list");
        assertEquals(2, output.size());
        assertEquals("*", output.get(0).get("Online"));
        assertEquals("*", output.get(1).get("Online"));

        // Create a managed ident file
        Path managedTokenPath = tmp.resolve("PATH_TO_MANAGED_TOKEN.txt").toAbsolutePath();
        remote(managed, RemoteCentralTool.class, "--managedIdent", "--output=" + managedTokenPath);

        // Connect managed to central and verify the connection
        output = remote(central, RemoteCentralTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(0, output.size());

        remote(central, RemoteCentralTool.class, "--instanceGroup=GROUP_NAME", "--attach=" + managedTokenPath,
                "--description=DESCRIPTION", "--uri=" + managed.getUri());

        output = remote(central, RemoteCentralTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals(managed.getUri().toString(), output.get(0).get("Uri"));
        assertEquals("DESCRIPTION", output.get(0).get("Description"));
        assertEquals("never", output.get(0).get("LastSync"));
        assertEquals("2", output.get(0).get("NumberOfLocalMinions"));
        assertEquals("0", output.get(0).get("NumberOfInstances"));
    }

    private void createAndUploadProduct(Path tmp) throws IOException {
        // Create necessary subdirectory
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));

        // Create the executeables
        Files.writeString(productPath.resolve("launch.bat"), "echo \"Successfully launched on WINDOWS\"");
        Files.writeString(productPath.resolve("launch.sh"), "echo \"Successfully launched on LINUX\"");

        // Create the product metadata
        Files.writeString(productPath.resolve("product-info.yaml"),//
                """
                        name: "Test Product"
                        product: "io.bdeploy/test"
                        vendor: BDeploy Team
                        applications:
                          - "test-app"
                        instanceTemplates:
                          - "instance-template.yaml"
                        versionFile: "product-version.yaml"
                        """);
        Files.writeString(productPath.resolve("product-version.yaml"),//
                """
                        version: "1.0.0"
                        appInfo:
                          test-app:
                            WINDOWS: "app-info.yaml"
                            LINUX: "app-info.yaml"
                            """);
        Files.writeString(productPath.resolve("app-info.yaml"),//
                """
                        name: "Test Application"
                        supportedOperatingSystems:
                          - WINDOWS
                          - LINUX
                        processControl:
                          gracePeriod: 3000
                          supportedStartTypes:
                            - MANUAL
                            - MANUAL_CONFIRM
                            - INSTANCE
                          supportsKeepAlive: true
                          noOfRetries: 2
                        startCommand:
                          launcherPath: "{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}"
                          """);
        Files.writeString(productPath.resolve("instance-template.yaml"),//
                """
                        name: Default Test Configuration
                        description: "Creates an instance with the default configuration"
                        processControlGroups:
                          - name: "First Group"
                            startType: "PARALLEL"
                            startWait: "WAIT"
                          - name: "Second Group"
                            stopType: "PARALLEL"
                        groups:
                          - name: "Only Group"
                            description: "The one and only group"
                            applications:
                              - application: test-app
                                name: "Test Application"
                                description: "Test Application that prints a single line to the standard output"
                                processControl:
                                  startType: "MANUAL_CONFIRM"
                                  """);

        // Push the product
        remote(central, ProductTool.class, "--instanceGroup=SOFTWARE_REPO_NAME", "--hive=" + bhivePath, "--import=" + productPath,
                "--push");

        // Force invalidate. Normally this is done after a short timeout when the spawn listener
        // will detect the change. However in this case we want to be able to continue immediately
        // and everything is running in the local JVM, so we can do it like this:
        ProductManifest.invalidateAllScanCaches();
    }

    private void doCheck() throws IOException {
        StructuredOutput output;

        // Check if the system was set up correctly
        output = remote(central, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        output = remote(central, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("", output.get(0).get("Installed"));
        assertEquals("", output.get(0).get("Active"));

        String instanceId = output.get(0).get("Id");
        String instanceVersion = output.get(0).get("Version");

        // Install the instance
        remote(central, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + instanceId,
                "--version=" + instanceVersion, "--install");
        output = remote(central, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("*", output.get(0).get("Installed"));
        assertEquals("", output.get(0).get("Active"));

        // Activate the instance
        remote(central, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + instanceId,
                "--version=" + instanceVersion, "--activate");
        output = remote(central, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("*", output.get(0).get("Installed"));
        assertEquals("*", output.get(0).get("Active"));
    }
}

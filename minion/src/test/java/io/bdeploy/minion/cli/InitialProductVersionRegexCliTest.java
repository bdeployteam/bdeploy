package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteSystemTool;
import io.bdeploy.ui.dto.InstanceDto;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(TestMinion.class)
class InitialProductVersionRegexCliTest extends BaseMinionCliTest {

    @Test
    void testSystemTemplateProductVersionRegexIsSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applySystemTemplate(remote, tmp, null, "1\\..*");
        validateProductFilterRegex(remote, "1\\..*");
    }

    @Test
    void testSystemTemplateInitialProductVersionRegexIsNotSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applySystemTemplate(remote, tmp, "1\\..*", null);
        validateProductFilterRegex(remote, ".*");
    }

    @Test
    void testSystempTemplateSpecifiesBothProductVersionRegexes(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applySystemTemplate(remote, tmp, "^1\\.0\\.\\d+$", "THISISNOTGONNABREAK");
        validateProductFilterRegex(remote, "THISISNOTGONNABREAK");
    }

    @Test
    void testSytemTemplateRegexesAreNotSet(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applySystemTemplate(remote, tmp, null, null);
        validateProductFilterRegex(remote, ".*");
    }

    @Test
    void testSystemTemplateHasNoMatchingProductForInitialProductVersionRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(NotFoundException.class, () -> applySystemTemplate(remote, tmp, "IDONOTEXIST", null));
    }

    @Test
    void testSystemTemplateHasNoMatchingProductForProductVersionRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(NotFoundException.class, () -> applySystemTemplate(remote, tmp, null, "IDONOTEXIST"));
    }

    @Test
    void testInstanceTemplateProductVersionRegexIsSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applyInstanceTemplate(remote, tmp, null, "1\\..*");
        validateProductFilterRegex(remote, "1\\..*");
    }

    @Test
    void testInstanceTemplateInitialProductVersionRegexIsNotSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applyInstanceTemplate(remote, tmp, "1\\..*", null);
        validateProductFilterRegex(remote, ".*");
    }

    @Test
    void testInstanceTemplateSpecifiesBothProductVersionRegexes(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applyInstanceTemplate(remote, tmp, "^1\\.0\\.\\d+$", "THISISNOTGONNABREAK");
        validateProductFilterRegex(remote, "THISISNOTGONNABREAK");
    }

    @Test
    void testInstanceTemplateRegexesAreNotSet(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applyInstanceTemplate(remote, tmp, null, null);
        validateProductFilterRegex(remote, ".*");
    }

    @Test
    void testInstanceTemplateHasNoMatchingProductForInitialProductVersionRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(NotFoundException.class, () -> applyInstanceTemplate(remote, tmp, "IDONOTEXIST", null));
    }

    @Test
    void testInstanceTemplateHasNoMatchingProductForProductVersionRegex(RemoteService remote, @TempDir Path tmp)
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(NotFoundException.class, () -> applyInstanceTemplate(remote, tmp, null, "IDONOTEXIST"));
    }

    private void validateProductFilterRegex(RemoteService remote, String regex) {
        // Check if the instance was set up correctly
        InstanceResource ir = ResourceProvider.getResource(remote, InstanceGroupResource.class, null)
                .getInstanceResource("GROUP_NAME");
        List<InstanceDto> instances = ir.list();
        assertEquals(1, instances.size());
        assertEquals(new Manifest.Key("io.bdeploy/test/product", "1.0.0"), instances.get(0).instanceConfiguration.product);
        assertEquals(regex, instances.get(0).instanceConfiguration.productFilterRegex);
    }

    private void applyInstanceTemplate(RemoteService remote, Path tmp, String initialProductVersionRegex,
            String productVersionRegex) throws IOException {
        // Create the instance template
        Path instanceTemplatePath = tmp.resolve("system-template.yaml");
        String template = """
                name: "Test Instance"
                description: "Instance To Test Product Version Regex Functionality"
                productId: "io.bdeploy/test"
                <PRODUCT_VERSION_REGEX>
                <INITIAL_PRODUCT_VERSION_REGEX>
                templateName: "Default Test Configuration"
                defaultMappings:
                  - group: "Only Group"
                    node: "master"
                        """;
        String ipvr = initialProductVersionRegex == null ? "" : "initialProductVersionRegex: " + initialProductVersionRegex;
        String pvr = productVersionRegex == null ? "" : "productVersionRegex: " + productVersionRegex;
        Files.writeString(instanceTemplatePath,
                template.replace("<INITIAL_PRODUCT_VERSION_REGEX>", ipvr).replace("<PRODUCT_VERSION_REGEX>", pvr));

        // Import the instance template
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_NAME",
                "--purpose=TEST", "--template=" + instanceTemplatePath);

        // Check if the instance was set up correctly
        StructuredOutput output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("INSTANCE_NAME", output.get(0).get("Name"));
        assertEquals("Instance To Test Product Version Regex Functionality", output.get(0).get("Description"));
        assertEquals("1", output.get(0).get("Version"));
        assertEquals("TEST", output.get(0).get("Purpose"));
        assertEquals("io.bdeploy/test/product", output.get(0).get("Product"));
        assertEquals("1.0.0", output.get(0).get("ProductVersion"));
        assertEquals("None", output.get(0).get("System"));
    }

    private void applySystemTemplate(RemoteService remote, Path tmp, String initialProductVersionRegex,
            String productVersionRegex) throws IOException {
        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        String template = """
                name: Test System
                description: SYSTEM_DESCRIPTION
                instances:
                  - name: "Test Instance"
                    description: "The Test Instance of the Test System"
                    productId: "io.bdeploy/test"
                    <PRODUCT_VERSION_REGEX>
                    <INITIAL_PRODUCT_VERSION_REGEX>
                    templateName: "Default Test Configuration"
                    defaultMappings:
                      - group: "Only Group"
                        node: "master"
                        """;
        String ipvr = initialProductVersionRegex == null ? "" : "initialProductVersionRegex: " + initialProductVersionRegex;
        String pvr = productVersionRegex == null ? "" : "productVersionRegex: " + productVersionRegex;
        Files.writeString(systemTemplatePath,
                template.replace("<INITIAL_PRODUCT_VERSION_REGEX>", ipvr).replace("<PRODUCT_VERSION_REGEX>", pvr));

        // Import the system template
        remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=SYSTEM_NAME", "--purpose=TEST",
                "--createFrom=" + systemTemplatePath);

        // Check if the system was set up correctly
        StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));
    }

    private void createInstanceGroup(RemoteService remote) {
        StructuredOutput output = remote(remote, RemoteInstanceGroupTool.class, "--list");
        assertEquals(0, output.size());

        remote(remote, RemoteInstanceGroupTool.class, "--create=GROUP_NAME");

        output = remote(remote, RemoteInstanceGroupTool.class, "--list");
        assertEquals(1, output.size());
        assertEquals("GROUP_NAME", output.get(0).get("Name"));
    }

    private void createAndUploadProduct(RemoteService remote, Path tmp) throws IOException {
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
        remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath, "--import=" + productPath,
                "--push");

        ProductManifest.invalidateAllScanCaches();

        StructuredOutput output = remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath,
                "--list");
        assertEquals(1, output.size());
        assertEquals("Test Product", output.get(0).get("Name"));
        assertEquals("1", output.get(0).get("NoOfInstanceTemplates"));
    }
}

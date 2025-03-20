package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteSystemTool;
import jakarta.ws.rs.ClientErrorException;

@ExtendWith(TestMinion.class)
class ResolvingTemplateVariablesCliTest extends BaseMinionCliTest {

    @Test
    void testDefaultInstanceIsOkViaSystemTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        createViaSystemTemplate(remote, tmp, product, null);
    }

    @Test
    void testDefaultInstanceIsOkViaInstanceTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        createViaInstanceTemplate(remote, tmp, product);
    }

    @Test
    void testGlobalParamIsOkViaSystemTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        var app = product.applications.get("app-info.yaml");
        var param = app.startCommand.parameters.stream().filter(p -> "param.sleep".equals(p.id)).findFirst().orElseThrow();
        param.global = true;
        createViaSystemTemplate(remote, tmp, product, null);
    }

    @Test
    void testGlobalParamIsOkViaInstanceTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        var app = product.applications.get("app-info.yaml");
        var param = app.startCommand.parameters.stream().filter(p -> "param.sleep".equals(p.id)).findFirst().orElseThrow();
        param.global = true;
        createViaInstanceTemplate(remote, tmp, product);
    }

    @Test
    void testTemplVarWithoutDefaultValueFailsViaSystemTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        var appTpl = product.applicationTemplates.get("app-template.yaml");
        var tplVar = appTpl.templateVariables.stream().filter(tv -> "app-tpl-sleep".equals(tv.id)).findFirst().orElseThrow();
        tplVar.defaultValue = null;
        createViaSystemTemplate(remote, tmp, product,
                "ERROR: Failed to apply template: java.lang.IllegalArgumentException: Cannot find replacement for variable T:app-tpl-sleep while processing {{T:app-tpl-sleep}}");
    }

    @Test
    void testTemplVarWithoutDefaultValueFailsViaInstanceTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        var product = TestProductFactory.generateProduct();
        var appTpl = product.applicationTemplates.get("app-template.yaml");
        var tplVar = appTpl.templateVariables.stream().filter(tv -> "app-tpl-sleep".equals(tv.id)).findFirst().orElseThrow();
        tplVar.defaultValue = null;
        assertThrows(ClientErrorException.class, () -> createViaInstanceTemplate(remote, tmp, product),
                "Template Variable app-tpl-sleep not provided, required in group Only Group");
    }

    private void createViaSystemTemplate(RemoteService remote, Path tmp, TestProductFactory.TestProductDescriptor product,
            String errorMessage) throws IOException {
        createInstanceGroup(remote);
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        TestProductFactory.writeProductToFile(productPath, product);
        uploadProduct(remote, bhivePath, productPath);
        if (errorMessage == null) {
            applySystemTemplate(remote, tmp);
        } else {
            applySystemTemplateAndExpectError(remote, tmp, errorMessage);
        }
    }

    private void createViaInstanceTemplate(RemoteService remote, Path tmp, TestProductFactory.TestProductDescriptor product)
            throws IOException {
        createInstanceGroup(remote);
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        TestProductFactory.writeProductToFile(productPath, product);
        uploadProduct(remote, bhivePath, productPath);
        applyInstanceTemplate(remote, tmp);
    }

    private void applySystemTemplateAndExpectError(RemoteService remote, Path tmp, String errorMessage) {
        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertEquals(1, output.size());
        assertEquals(errorMessage, output.get(0).get("TestInstance"));

        // Check if the system was set up correctly
        output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check that instance was not created
        output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(0, output.size());
    }

    private void applySystemTemplate(RemoteService remote, Path tmp) throws IOException {
        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=SYSTEM_NAME", "--purpose=TEST",
                "--createFrom=" + systemTemplatePath);

        // Check if the system was set up correctly
        StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("Test Instance", output.get(0).get("Name"));
        assertEquals("The Test Instance of the Test System", output.get(0).get("Description"));
        assertEquals("1", output.get(0).get("Version"));
        assertEquals("TEST", output.get(0).get("Purpose"));
        assertEquals("io.bdeploy/test/product", output.get(0).get("Product"));
        assertEquals("1.0.0", output.get(0).get("ProductVersion"));
        assertTrue(output.get(0).get("System").contains(systemId));
    }

    private void applyInstanceTemplate(RemoteService remote, Path tmp) throws IOException {
        // Create the instance template
        Path instanceTemplatePath = tmp.resolve("instance-response.yaml");
        InstanceTemplateReferenceDescriptor instanceTemplate = TestProductFactory.generateInstanceTemplateReference();
        TestProductFactory.writeToFile(instanceTemplatePath, instanceTemplate);

        // Import the instance template
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_NAME",
                "--purpose=TEST", "--template=" + instanceTemplatePath);

        // Check if the instance was set up correctly
        StructuredOutput output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("INSTANCE_NAME", output.get(0).get("Name"));
        assertEquals("Instance From TestProductFactory", output.get(0).get("Description"));
        assertEquals("1", output.get(0).get("Version"));
        assertEquals("TEST", output.get(0).get("Purpose"));
        assertEquals("io.bdeploy/test/product", output.get(0).get("Product"));
        assertEquals("1.0.0", output.get(0).get("ProductVersion"));
        assertEquals("None", output.get(0).get("System"));
    }
}

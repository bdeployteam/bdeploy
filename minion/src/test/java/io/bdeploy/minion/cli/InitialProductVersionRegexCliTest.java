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
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
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
        Path instanceTemplatePath = tmp.resolve("instance-response.yaml");
        InstanceTemplateReferenceDescriptor instanceTemplate = TestProductFactory.generateInstanceTemplateReference();
        instanceTemplate.initialProductVersionRegex = initialProductVersionRegex;
        instanceTemplate.productVersionRegex = productVersionRegex;
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

    private void applySystemTemplate(RemoteService remote, Path tmp, String initialProductVersionRegex,
            String productVersionRegex) throws IOException {
        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.instances.get(0).productVersionRegex = productVersionRegex;
        systemTemplate.instances.get(0).initialProductVersionRegex = initialProductVersionRegex;
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=SYSTEM_NAME", "--purpose=TEST",
                "--createFrom=" + systemTemplatePath);

        // Check if the system was set up correctly
        StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));
    }

    private void createAndUploadProduct(RemoteService remote, Path tmp) throws IOException {
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        TestProductFactory.writeProductToFile(productPath, TestProductFactory.generateProduct());
        uploadProduct(remote, bhivePath, productPath);
    }
}

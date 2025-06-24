package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
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
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteSystemTool;
import io.bdeploy.ui.dto.InstanceDto;
import jakarta.ws.rs.ClientErrorException;

@ExtendWith(TestMinion.class)
class InitialProductVersionRegexCliTest extends BaseMinionCliTest {

    // System template tests

    @Test
    void testSystemTemplateProductVersionRegexIsSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithSystemTemplate(remote, tmp, null, "1\\..*", "1\\..*");
    }

    @Test
    void testSystemTemplateInitialProductVersionRegexIsNotSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithSystemTemplate(remote, tmp, "1\\..*", null, ".*");
    }

    @Test
    void testSystemTemplateSpecifiesBothProductVersionRegexes(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithSystemTemplate(remote, tmp, "^1\\.0\\.\\d+$", "THISISNOTGONNABREAK", "THISISNOTGONNABREAK");
    }

    @Test
    void testSystemTemplateRegexesAreNotSet(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithSystemTemplate(remote, tmp, null, null, ".*");
    }

    @Test
    void testSystemTemplateHasNoMatchingProductForInitialProductVersionRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestExceptionWithSystemTemplate(remote, tmp, "IDONOTEXIST", null);
    }

    @Test
    void testSystemTemplateHasNoMatchingProductForProductVersionRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestExceptionWithSystemTemplate(remote, tmp, null, "IDONOTEXIST");
    }

    private void doTestHappyFlowWithSystemTemplate(RemoteService remote, Path tmp, String r1, String r2, String regex)//
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applySystemTemplate(remote, tmp, r1, r2);
        validateProductFilterRegex(remote, regex);
    }

    private void doTestExceptionWithSystemTemplate(RemoteService remote, Path tmp, String r1, String r2)//
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(ClientErrorException.class, () -> applySystemTemplate(remote, tmp, r1, r2));
    }

    private void applySystemTemplate(RemoteService remote, Path tmp, String r1, String r2) {
        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.instances.get(0).productVersionRegex = r2;
        systemTemplate.instances.get(0).initialProductVersionRegex = r1;
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

    // Instance template tests

    @Test
    void testInstanceTemplateProductVersionRegexIsSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithInstanceTemplate(remote, tmp, null, "1\\..*", "1\\..*");
    }

    @Test
    void testInstanceTemplateInitialProductVersionRegexIsNotSavedAsProductFilterRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithInstanceTemplate(remote, tmp, "1\\..*", null, ".*");
    }

    @Test
    void testInstanceTemplateSpecifiesBothProductVersionRegexes(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithInstanceTemplate(remote, tmp, "^1\\.0\\.\\d+$", "THISISNOTGONNABREAK", "THISISNOTGONNABREAK");
    }

    @Test
    void testInstanceTemplateRegexesAreNotSet(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestHappyFlowWithInstanceTemplate(remote, tmp, null, null, ".*");
    }

    @Test
    void testInstanceTemplateHasNoMatchingProductForInitialProductVersionRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestExceptionWithInstanceTemplate(remote, tmp, "IDONOTEXIST", null);
    }

    @Test
    void testInstanceTemplateHasNoMatchingProductForProductVersionRegex(RemoteService remote, @TempDir Path tmp)//
            throws IOException {
        doTestExceptionWithInstanceTemplate(remote, tmp, null, "IDONOTEXIST");
    }

    private void doTestHappyFlowWithInstanceTemplate(RemoteService remote, Path tmp, String r1, String r2, String regex)//
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        applyInstanceTemplate(remote, tmp, r1, r2);
        validateProductFilterRegex(remote, regex);
    }

    private void doTestExceptionWithInstanceTemplate(RemoteService remote, Path tmp, String r1, String r2)//
            throws IOException {
        createInstanceGroup(remote);
        createAndUploadProduct(remote, tmp);
        assertThrows(ClientErrorException.class, () -> applyInstanceTemplate(remote, tmp, r1, r2));
    }

    private void applyInstanceTemplate(RemoteService remote, Path tmp, String r1, String r2) {
        // Create the instance template
        Path instanceTemplatePath = tmp.resolve("instance-response.yaml");
        InstanceTemplateReferenceDescriptor instanceTemplate = TestProductFactory.generateInstanceTemplateReference();
        instanceTemplate.initialProductVersionRegex = r1;
        instanceTemplate.productVersionRegex = r2;
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

    // Utility methods
    private static void validateProductFilterRegex(RemoteService remote, String regex) {
        List<InstanceDto> instances = ResourceProvider.getResource(remote, InstanceGroupResource.class, null)
                .getInstanceResource("GROUP_NAME").list();
        assertEquals(1, instances.size());
        assertEquals(new Manifest.Key("io.bdeploy/test/product", "1.0.0"), instances.get(0).instanceConfiguration.product);
        assertEquals(regex, instances.get(0).instanceConfiguration.productFilterRegex);
    }
}

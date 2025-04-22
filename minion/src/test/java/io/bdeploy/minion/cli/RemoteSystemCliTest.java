package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteSystemTool;

@ExtendWith(TestMinion.class)
public class RemoteSystemCliTest extends BaseMinionCliTest {

    @Test
    void testSystemCreatedWithTemplate(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        // instance template with unset properties - unset in system template - check defaults
        InstanceTemplateDescriptor smallInstanceTemplate = TestProductFactory.generateMinimalInstanceTemplate("Small instance");
        // instance template with unset properties - unset in system template - check defaults
        InstanceTemplateDescriptor mediumInstanceTemplate = TestProductFactory.generateMinimalInstanceTemplate("Medium instance");
        // instance template with all properties set
        InstanceTemplateDescriptor instanceTemplate = TestProductFactory.generateInstanceTemplate();
        product.instanceTemplates = Map.of("min-instance-template.yaml", smallInstanceTemplate, "medium-instance-template.yaml",
                mediumInstanceTemplate, "instance-template.yaml", instanceTemplate);
        product.descriptor.instanceTemplates = List.of("min-instance-template.yaml", "medium-instance-template.yaml",
                "instance-template.yaml");

        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.instances = new ArrayList<>();
        // instance template with unset properties - unset in system template - check defaults
        InstanceTemplateReferenceDescriptor referenceWoOverrides = TestProductFactory.generateInstanceTemplateReference(
                "no overrides instance", smallInstanceTemplate.name);
        referenceWoOverrides.autoStart = null;
        referenceWoOverrides.autoUninstall = null;
        systemTemplate.instances.add(referenceWoOverrides);

        // instance template with unset in instance template, but set in system
        InstanceTemplateReferenceDescriptor referenceWithSystemValues = TestProductFactory.generateInstanceTemplateReference(
                "instance with system values", smallInstanceTemplate.name);
        referenceWithSystemValues.autoStart = true;
        referenceWithSystemValues.autoUninstall = false;
        systemTemplate.instances.add(referenceWithSystemValues);

        // this attempts to have all properties set and check which ones are used: instance or system
        InstanceTemplateReferenceDescriptor referenceWithOverrides = TestProductFactory.generateInstanceTemplateReference(
                "instance with overrides", instanceTemplate.name);
        referenceWithOverrides.autoStart = false;
        referenceWithOverrides.autoUninstall = true;
        systemTemplate.instances.add(referenceWithOverrides);
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("NoOverridesInstance").contains("Successfully created instance with ID"));
        assertTrue(systemCreationOutput.get(0).get("InstanceWithSystemValues").contains("Successfully created instance with ID"));
        assertTrue(systemCreationOutput.get(0).get("InstanceWithOverrides").contains("Successfully created instance with ID"));

        // Check if the system was set up correctly
        TestCliTool.StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> mappedInstancesRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(3, mappedInstancesRows.size());

        doInstanceWithNoOverridesChecks(mappedInstancesRows.get("no overrides instance"), systemId);
        doInstanceWithSystemValuesCheck(mappedInstancesRows.get("instance with system values"), systemId);
        doFullInstanceChecks(mappedInstancesRows.get("instance with overrides"), systemId);
    }

    private static void doInstanceWithNoOverridesChecks(TestCliTool.StructuredOutputRow instance, String systemId) {
        assertEquals("Instance From TestProductFactory", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        assertEquals("", instance.get("AutoStart"));
        assertEquals("*", instance.get("AutoUninstall"));
    }

    private static void doInstanceWithSystemValuesCheck(TestCliTool.StructuredOutputRow instance, String systemId) {
        assertEquals("Instance From TestProductFactory", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        assertEquals("*", instance.get("AutoStart"));
        assertEquals("", instance.get("AutoUninstall"));
    }

    private static void doFullInstanceChecks(TestCliTool.StructuredOutputRow instance, String systemId) {
        assertEquals("Instance From TestProductFactory", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        // system template values are completely overridden
        assertEquals("", instance.get("AutoStart"));
        assertEquals("*", instance.get("AutoUninstall"));
    }

}

package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteProcessConfigTool;
import io.bdeploy.ui.cli.RemoteProcessTool;
import io.bdeploy.ui.cli.RemoteSystemTool;

@ExtendWith(TestMinion.class)
class RemoteSystemCliTest extends BaseMinionCliTest {

    /*
     * This test gathers the cases with minimal amount of setup to check defaults.
     */
    @Test
    void testCreateSystemWithMinimalSetup(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        // instance template with unset properties - unset in system template - check defaults
        systemTemplate.instances.clear();
        InstanceTemplateReferenceDescriptor referenceWoOverrides = TestProductFactory.generateInstanceTemplateReference(
                "no overrides instance", "Small instance");
        referenceWoOverrides.autoStart = null;
        referenceWoOverrides.autoUninstall = null;
        systemTemplate.instances.add(referenceWoOverrides);

        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("NoOverridesInstance").contains("Successfully created instance with ID"));

        // Check if the system was set up correctly
        TestCliTool.StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> mappedInstancesRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(1, mappedInstancesRows.size());

        TestCliTool.StructuredOutputRow instance = mappedInstancesRows.get("no overrides instance");
        assertEquals("Instance From TestProductFactory", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        assertEquals("", instance.get("AutoStart"));
        assertEquals("*", instance.get("AutoUninstall"));

        String uuid = instance.get("Id");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--activate");
        Map<String, TestCliTool.StructuredOutputRow> processRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteProcessTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--list");
        assertEquals(4, processRows.size());

        /* check app done with template - Server With Sleep */
        String minApplicationId = processRows.get("Application 1").get("Id");
        Map<String, TestCliTool.StructuredOutputRow> application1Params = doRemoteAndIndexOutputOn("Id", remote,
                RemoteProcessConfigTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--process=" + minApplicationId,
                "--showParameters");
        assertEquals(1, application1Params.size());
        TestCliTool.StructuredOutputRow sleepParameterRow = application1Params.get("param.sleep");
        assertEquals("Sleep Timeout", sleepParameterRow.get("Name"));
        assertEquals("10", sleepParameterRow.get("Value"));
        assertEquals("NUMERIC", sleepParameterRow.get("Type"));
        assertEquals("", sleepParameterRow.get("Custom"));
        assertEquals("", sleepParameterRow.get("Fixed"));
        assertEquals("*", sleepParameterRow.get("Mandatory"));
        assertEquals("10", sleepParameterRow.get("Default"));

        // param value will be default value from template
        assertEquals("750", getSleepParamValue(remote, uuid, processRows.get("Application 2")));

        // param value will be fixed variable value from app template
        assertEquals("720", getSleepParamValue(remote, uuid, processRows.get("Application 3")));

        // param value will be fixed variable value from app template
        assertEquals("700", getSleepParamValue(remote, uuid, processRows.get("Application 4")));
    }

    /*
     * This test gathers the cases in which the values are in the instance template.
     */
    @Test
    void testCreateSystemWithInstanceThatUsesTemplateValues(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        InstanceTemplateDescriptor instanceTemplateDescriptor = product.instanceTemplates.get("instance-template.yaml");
        InstanceTemplateGroup onlyGroup = instanceTemplateDescriptor.groups.getFirst();
        onlyGroup.applications = TestProductFactory.generateApplicationsForInstanceTemplate();
        // app 1 - override parameter
        onlyGroup.applications.getFirst().startParameters.add(TestProductFactory.aTemplateParameter("param.sleep", "500"));
        // app 2 - template variable redefined in instance template
        instanceTemplateDescriptor.templateVariables.add(TestProductFactory.aTemplateVariable("app-tpl-sleep", "550"));
        // app 3 fixed value in app template, nothing in instance template
        // app 4 will have fixed variables in instance template and in app template
        onlyGroup.applications.get(3).fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep-2", "650"));
        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        // instance template with set values, but no overrides in the system template
        InstanceTemplateReferenceDescriptor referenceWithTemplateValues = systemTemplate.instances.getFirst();
        referenceWithTemplateValues.autoStart = null;
        referenceWithTemplateValues.autoUninstall = null;
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("TestInstance").contains("Successfully created instance with ID"));

        // Check if the system was set up correctly
        TestCliTool.StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> mappedInstancesRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(1, mappedInstancesRows.size());

        /* check instance stats */
        TestCliTool.StructuredOutputRow instance = mappedInstancesRows.get("Test Instance");
        assertEquals("The Test Instance of the Test System", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        assertEquals("*", instance.get("AutoStart"));
        assertEquals("", instance.get("AutoUninstall"));

        String uuid = instance.get("Id");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--activate");
        Map<String, TestCliTool.StructuredOutputRow> processRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteProcessTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--list");
        assertEquals(4, processRows.size());

        // startParam in instance template overrides param in app
        assertEquals("500", getSleepParamValue(remote, uuid, processRows.get("Application 1")));

        // default template variable value in instance template will override the one from app template
        assertEquals("550", getSleepParamValue(remote, uuid, processRows.get("Application 2")));

        // fixed variable in app template being used, nothing in instance template
        assertEquals("720", getSleepParamValue(remote, uuid, processRows.get("Application 3")));

        // fixed variable in instance template overrides the one in app template
        assertEquals("650", getSleepParamValue(remote, uuid, processRows.get("Application 4")));
    }

    /*
     * This test gathers the cases in which the values are defined in the system template.
     */
    @Test
    void testCreateSystemWithInstanceThatIsDefinedInReference(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        // removing variables from app template to be able to override them
        product.applicationTemplates.get("app-template-3.yaml").templateVariables.clear();
        product.applicationTemplates.get("app-template-3.yaml").fixedVariables.clear();
        InstanceTemplateDescriptor instanceTemplateDescriptor = product.instanceTemplates.get("min-instance-template.yaml");
        InstanceTemplateGroup onlyGroup = instanceTemplateDescriptor.groups.getFirst();
        onlyGroup.applications.getFirst().startParameters.add(
                TestProductFactory.aTemplateParameter("param.sleep", "{{T:app-tpl-sleep}}"));
        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.templateVariables.add(TestProductFactory.aTemplateVariable("app-tpl-sleep", "200"));

        systemTemplate.instances.clear();
        // instance template with unset values, but set in system
        InstanceTemplateReferenceDescriptor referenceWithSystemValues = TestProductFactory.generateInstanceTemplateReference(
                "instance with system values", "Small instance");
        referenceWithSystemValues.fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep-2", "300"));
        referenceWithSystemValues.autoStart = true;
        referenceWithSystemValues.autoUninstall = false;
        systemTemplate.instances.add(referenceWithSystemValues);
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("InstanceWithSystemValues").contains("Successfully created instance with ID"));

        // Check if the system was set up correctly
        TestCliTool.StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> mappedInstancesRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(1, mappedInstancesRows.size());

        TestCliTool.StructuredOutputRow instance = mappedInstancesRows.get("instance with system values");
        assertEquals("Instance From TestProductFactory", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        assertEquals("*", instance.get("AutoStart"));
        assertEquals("", instance.get("AutoUninstall"));

        String uuid = instance.get("Id");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--activate");
        Map<String, TestCliTool.StructuredOutputRow> processRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteProcessTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--list");
        assertEquals(4, processRows.size());

        // does not have template variables and will be default
        assertEquals("200", getSleepParamValue(remote, uuid, processRows.get("Application 1")));

        // default template variable value in system template will override the one from app template
        assertEquals("200", getSleepParamValue(remote, uuid, processRows.get("Application 2")));

        // fixedVariable from app template being used, NOT the default redefined value in the system template
        assertEquals("720", getSleepParamValue(remote, uuid, processRows.get("Application 3")));

        // fixed variable in system template being used, nothing in app template
        assertEquals("300", getSleepParamValue(remote, uuid, processRows.get("Application 4")));
    }

    /*
     * This test gathers the cases where everything is everywhere.
     */
    @Test
    void testCreateSystemWithInstanceWithFullOverrides(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        InstanceTemplateDescriptor instanceTemplateDescriptor = product.instanceTemplates.get("instance-template.yaml");
        InstanceTemplateGroup onlyGroup = instanceTemplateDescriptor.groups.getFirst();
        onlyGroup.applications = TestProductFactory.generateApplicationsForInstanceTemplate();
        // app 1 - add a start param that will only be resolved from the system template
        onlyGroup.applications.get(0).startParameters.add(
                TestProductFactory.aTemplateParameter("param.sleep", "{{T:sys-sleep-tpl}}"));
        // template variable defined in system, instance and app template, but no fixed variable for it
        instanceTemplateDescriptor.templateVariables.add(TestProductFactory.aTemplateVariable("app-tpl-sleep", "550"));
        // fixed variable should be defined everywhere
        onlyGroup.applications.get(3).fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep-2", "350"));
        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.templateVariables.add(TestProductFactory.aTemplateVariable("sys-sleep-tpl", "400"));
        systemTemplate.templateVariables.add(TestProductFactory.aTemplateVariable("app-tpl-sleep", "250"));
        InstanceTemplateReferenceDescriptor referenceWithOverrides = systemTemplate.instances.getFirst();
        referenceWithOverrides.fixedVariables.add(new TemplateVariableFixedValueOverride("sys-sleep-tpl", "450"));
        referenceWithOverrides.fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep-2", "300"));
        referenceWithOverrides.autoStart = false;
        referenceWithOverrides.autoUninstall = true;
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("TestInstance").contains("Successfully created instance with ID"));

        // Check if the system was set up correctly
        TestCliTool.StructuredOutput output = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        String systemId = output.get(0).get("Id");
        assertEquals("SYSTEM_NAME", output.get(0).get("Name"));
        assertEquals("SYSTEM_DESCRIPTION", output.get(0).get("Description"));

        // Check if the instance was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> mappedInstancesRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(1, mappedInstancesRows.size());

        /* check instance stats */
        TestCliTool.StructuredOutputRow instance = mappedInstancesRows.get("Test Instance");
        assertEquals("The Test Instance of the Test System", instance.get("Description"));
        assertEquals("1", instance.get("Version"));
        assertEquals("TEST", instance.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instance.get("Product"));
        assertEquals("1.0.0", instance.get("ProductVersion"));
        assertTrue(instance.get("System").contains(systemId));
        // system template values are completely overridden
        assertEquals("", instance.get("AutoStart"));
        assertEquals("*", instance.get("AutoUninstall"));

        String uuid = instance.get("Id");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--activate");
        Map<String, TestCliTool.StructuredOutputRow> processRows = doRemoteAndIndexOutputOn("Name", remote,
                RemoteProcessTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--list");
        assertEquals(4, processRows.size());

        // set in template variable in system, but is link expression as start param in instance template
        assertEquals("450", getSleepParamValue(remote, uuid, processRows.get("Application 1")));

        // default template variable value in system template will override all the other defaults
        assertEquals("250", getSleepParamValue(remote, uuid, processRows.get("Application 2")));

        // fixed variable in app template overrides all other defaults
        assertEquals("720", getSleepParamValue(remote, uuid, processRows.get("Application 3")));

        // fixed variable in system, instance, and app template -> instance fixed variable should be used
        assertEquals("350", getSleepParamValue(remote, uuid, processRows.get("Application 4")));
    }

    private String getSleepParamValue(RemoteService remote, String uuid, TestCliTool.StructuredOutputRow processRow) {
        String processId = processRow.get("Id");
        Map<String, TestCliTool.StructuredOutputRow> appParams = doRemoteAndIndexOutputOn("Id", remote,
                RemoteProcessConfigTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--process=" + processId,
                "--showParameters");

        return appParams.get("param.sleep").get("Value");
    }

}

package io.bdeploy.minion.cli;

import static io.bdeploy.minion.cli.MultiNodeTestActions.attachMultiNodes;
import static io.bdeploy.minion.cli.MultiNodeTestActions.createMultiNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.MultiNodeMaster;
import io.bdeploy.minion.TestMinion.MultiNodeCompletion;
import io.bdeploy.minion.TestMinion.SourceMinion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import io.bdeploy.ui.cli.RemoteProcessConfigTool;
import io.bdeploy.ui.cli.RemoteProcessTool;
import io.bdeploy.ui.cli.RemoteSystemTool;

class RemoteSystemCliTest extends BaseMinionCliTest {

    private static final String MULTINODE_NAME = "multiNode";
    private static final String RUNTIME_NODE_NAME = "runtimeNode";

    @RegisterExtension
    private final TestMinion exStandalone = new TestMinion(MinionMode.STANDALONE);
    @RegisterExtension
    private final TestMinion exRuntimeNode = new TestMinion(MinionMode.NODE, RUNTIME_NODE_NAME, MinionDto.MinionNodeType.MULTI_RUNTIME);

    /*
     * This test gathers the cases with minimal amount of setup to check defaults.
     */
    @Test
    void testCreateSystemWithMinimalSetup(@SourceMinion(MinionMode.STANDALONE) RemoteService  remote, @TempDir Path tmp) throws IOException {
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
    void testCreateSystemWithInstanceThatUsesTemplateValues(@SourceMinion(MinionMode.STANDALONE) RemoteService  remote, @TempDir Path tmp) throws IOException {
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
    void testCreateSystemWithInstanceThatIsDefinedInReference(@SourceMinion(MinionMode.STANDALONE) RemoteService  remote, @TempDir Path tmp) throws IOException {
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
    void testCreateSystemWithInstanceWithFullOverrides(@SourceMinion(MinionMode.STANDALONE) RemoteService  remote, @TempDir Path tmp) throws IOException {
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

    @Test
    void testActionsOnAMultiNode(@SourceMinion(MinionMode.STANDALONE) RemoteService remote,
            @MultiNodeMaster(MULTINODE_NAME) MultiNodeMasterFile masterFile,
            @SourceMinion(value = MinionMode.NODE, disambiguation = "runtimeNode") MultiNodeCompletion runtimeNode,
            @TempDir Path tmp) throws IOException, InterruptedException {
        createMultiNode(remote, MULTINODE_NAME);

        createInstanceGroup(remote);

        Path bhivePath = Files.createDirectory(tmp.resolve("bhive"));
        var product = TestProductFactory.generateProduct();
        product.descriptor.minMinionVersion = "0.0.0";
        uploadProduct(remote, tmp, bhivePath, product);

        // Create the system template
        Path systemTemplatePath = tmp.resolve("system-template.yaml");
        SystemTemplateDescriptor systemTemplate = TestProductFactory.generateSystemTemplate();
        systemTemplate.instances.get(0).defaultMappings.getFirst().node = MULTINODE_NAME;
        TestProductFactory.writeToFile(systemTemplatePath, systemTemplate);

        // Import the system template
        var systemCreationOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=SYSTEM_NAME", "--purpose=TEST", "--createFrom=" + systemTemplatePath);
        assertTrue(systemCreationOutput.get(0).get("TestInstance").contains("Successfully created instance with ID"));
        String instanceUuid = systemCreationOutput.get(0).get("TestInstance")
                .replaceFirst("OK: Successfully created instance with ID ", "");

        // Listing system output
        var systemListOutput = doRemoteAndIndexOutputOn("Name", remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME",
                "--list");
        assertEquals(1, systemListOutput.size());
        String systemUuid = systemListOutput.get("SYSTEM_NAME").get("Id");

        // Check system info display
        var systemInfoOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + systemUuid,
                "--info");
        assertEquals("Info for System " + systemUuid + " - SYSTEM_NAME", systemInfoOutput.get(0).get("message"));
        assertEquals("", systemInfoOutput.get(0).get("ConfigVariables"));
        assertEquals("SYSTEM_DESCRIPTION", systemInfoOutput.get(0).get("Description"));

        // Check system status display
        var systemStatusOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + systemUuid,
                "--status");
        assertEquals("Test Instance", systemStatusOutput.get(0).get("InstanceName"));
        assertEquals(instanceUuid, systemStatusOutput.get(0).get("InstanceUuid"));
        assertEquals("", systemStatusOutput.get(0).get("ProcessName"));
        assertEquals("", systemStatusOutput.get(0).get("ProcessUuid"));
        assertEquals("", systemStatusOutput.get(0).get("Node"));
        assertEquals("WARNING", systemStatusOutput.get(0).get("Status"));
        assertEquals("Never", systemStatusOutput.get(0).get("LastSync"));
        assertEquals("Status unknown", systemStatusOutput.get(0).get("Messages"));

        // do install, activate and start and check details
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + instanceUuid, "--version=1",
                "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + instanceUuid, "--version=1",
                "--activate");

        var startSystemOutput = remote(remote, RemoteSystemTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + systemUuid,
                "--start");
        assertEquals(systemUuid, startSystemOutput.get(0).get("System"));
        assertEquals(instanceUuid, startSystemOutput.get(0).get("Instance"));
        assertEquals("INFO", startSystemOutput.get(0).get("Type"));
        assertEquals("Started", startSystemOutput.get(0).get("Message"));

        // No nodes attached so nothing to do
        var systemDetailsOutput = doRemoteAndIndexOutputOn("ProcessName", remote, RemoteSystemTool.class,
                "--instanceGroup=GROUP_NAME", "--uuid=" + systemUuid, "--status", "--details");
        assertEquals(1, systemDetailsOutput.size());

        TestCliTool.StructuredOutputRow instanceRow = systemDetailsOutput.get("");
        assertEquals("Test Instance", instanceRow.get("InstanceName"));
        assertEquals(instanceUuid, instanceRow.get("InstanceUuid"));
        assertEquals("", instanceRow.get("ProcessUuid"));
        assertEquals("", instanceRow.get("Node"));
        assertEquals("STOPPED", instanceRow.get("Status"));
        assertNotEquals("", instanceRow.get("LastSync"));
        assertEquals("", instanceRow.get("Messages"));

        // attach multi and check status again
        attachMultiNodes(remote, masterFile, runtimeNode);
        systemDetailsOutput = doRemoteAndIndexOutputOn("ProcessName", remote, RemoteSystemTool.class,
                "--instanceGroup=GROUP_NAME", "--uuid=" + systemUuid, "--status", "--details");
        assertEquals(3, systemDetailsOutput.size());

        instanceRow = systemDetailsOutput.get("");
        assertEquals("Test Instance", instanceRow.get("InstanceName"));
        assertEquals(instanceUuid, instanceRow.get("InstanceUuid"));
        assertEquals("", instanceRow.get("ProcessUuid"));
        assertEquals("", instanceRow.get("Node"));
        assertEquals("STOPPED", instanceRow.get("Status"));
        assertNotEquals("", instanceRow.get("LastSync"));
        assertEquals("", instanceRow.get("Messages"));

        TestCliTool.StructuredOutputRow serverWithSleepRow = systemDetailsOutput.get("Server With Sleep");
        assertEquals("Test Instance", serverWithSleepRow.get("InstanceName"));
        assertEquals(instanceUuid, serverWithSleepRow.get("InstanceUuid"));
        assertNotEquals("", serverWithSleepRow.get("ProcessUuid"));
        assertEquals("multiNode/NODE-multi-runtimeNode", serverWithSleepRow.get("Node"));
        assertEquals("STOPPED", serverWithSleepRow.get("Status"));
        assertEquals("", serverWithSleepRow.get("LastSync"));
        assertEquals("", serverWithSleepRow.get("Messages"));

        TestCliTool.StructuredOutputRow testServerAppRow = systemDetailsOutput.get("Test Server Application");
        assertEquals("Test Instance", testServerAppRow.get("InstanceName"));
        assertEquals(instanceUuid, testServerAppRow.get("InstanceUuid"));
        assertNotEquals("", testServerAppRow.get("ProcessUuid"));
        assertEquals("multiNode/NODE-multi-runtimeNode", testServerAppRow.get("Node"));
        assertEquals("STOPPED", testServerAppRow.get("Status"));
        assertEquals("", testServerAppRow.get("LastSync"));
        assertEquals("", testServerAppRow.get("Messages"));
    }

    private String getSleepParamValue(@SourceMinion(MinionMode.STANDALONE) RemoteService remote, String uuid,
            TestCliTool.StructuredOutputRow processRow) {
        String processId = processRow.get("Id");
        Map<String, TestCliTool.StructuredOutputRow> appParams = doRemoteAndIndexOutputOn("Id", remote,
                RemoteProcessConfigTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--process=" + processId,
                "--showParameters");

        return appParams.get("param.sleep").get("Value");
    }

}

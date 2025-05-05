package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateInstanceTemplateGroupMapping;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.cli.TestProductFactory.TestProductDescriptor;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;
import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteInstanceCliTest extends BaseMinionCliTest {

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp) throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        StructuredOutput result;

        /* At first we have only one version of instance aaa-bbb-ccc */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("TEST", result.get(0).get("Purpose"));

        /* update purpose to DEVELOPMENT to create a second instance version */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--update", "--uuid=" + id,
                "--purpose=DEVELOPMENT", "--enableAutoStart", "--disableAutoUninstall");

        assertEquals("Success", result.get(0).get("message"));
        assertEquals("DEVELOPMENT", result.get(0).get("NewPurpose"));

        /* update purpose to PRODUCTIVE to create a third instance version */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--update", "--uuid=" + id,
                "--purpose=PRODUCTIVE", "--productVersionRegex=1.*");

        assertEquals("Success", result.get(0).get("message"));
        assertEquals("PRODUCTIVE", result.get(0).get("NewPurpose"));

        /* list --all returns all 3 versions */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        assertEquals(3, result.size());
        assertEquals("3", result.get(0).get("Version"));
        assertEquals("PRODUCTIVE", result.get(0).get("Purpose"));
        assertEquals("2", result.get(1).get("Version"));
        assertEquals("DEVELOPMENT", result.get(1).get("Purpose"));
        assertEquals("1", result.get(2).get("Version"));
        assertEquals("TEST", result.get(2).get("Purpose"));

        /* since there are no active or installed versions, list by default will return latest version (3) */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list");

        // here we check all fields that are listed
        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("3", result.get(0).get("Version"));
        assertEquals("", result.get(0).get("Installed")); // not installed
        assertEquals("", result.get(0).get("Active")); // not active
        assertEquals("*", result.get(0).get("AutoStart"));
        assertEquals("", result.get(0).get("AutoUninstall"));
        assertEquals("PRODUCTIVE", result.get(0).get("Purpose"));
        assertEquals("customer/product", result.get(0).get("Product"));
        assertEquals("1.0.0.1234", result.get(0).get("ProductVersion"));
        assertEquals("1.*", result.get(0).get("ProductVerRegex"));
        assertEquals("None", result.get(0).get("System"));

        /* let's install first (1) and second (2) versions */
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=2", "--install");

        /*
         * since there are no active versions, list by default will return latest installed version.
         * since there are only two installed versions: (1) and (2), list will return (2) as it is the latest one
         */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("2", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("", result.get(0).get("Active")); // not active
        assertEquals("DEVELOPMENT", result.get(0).get("Purpose"));

        /* let's activate first (1) version */
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=1", "--activate");

        /* since there is an active version (1), list by default will return it. */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("*", result.get(0).get("Active")); // active
        assertEquals("TEST", result.get(0).get("Purpose"));
        // checking to see default values that were not supplied when creating the instance
        assertEquals("", result.get(0).get("AutoStart"));
        assertEquals("*", result.get(0).get("AutoUninstall"));
        assertEquals("", result.get(0).get("ProductVerRegex"));

        /* even though we have 3 versions to display, limit = 2 should return only 2 entries */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all", "--limit=2");
        assertEquals(2, result.size());

        /* filter by version */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all", "--version=2");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("2", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("", result.get(0).get("Active")); // active
        assertEquals("DEVELOPMENT", result.get(0).get("Purpose"));

        /** filter by purpose */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all", "--purpose=TEST");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("*", result.get(0).get("Active")); // active
        assertEquals("TEST", result.get(0).get("Purpose"));

        /* let's delete instance */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + id, "--delete", "--yes");
        assertEquals("aaa-bbb-ccc", result.get(0).get("Instance"));
        assertEquals("Deleted", result.get(0).get("Result"));

        /* list will return nothing, since instance is deleted */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        assertEquals(0, result.size());

        /* let's create 2 instances */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--create", "--name=unitTestInstance1",
                "--purpose=DEVELOPMENT", "--product=customer/product", "--productVersion=1.0.0.1234");
        String firstInstanceId = result.get(0).get("InstanceId");

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--create", "--name=unitTestInstance2",
                "--purpose=PRODUCTIVE", "--product=customer/product", "--productVersion=1.0.0.1234");
        String secondInstanceId = result.get(0).get("InstanceId");
        String bothInstances = firstInstanceId + "," + secondInstanceId;

        Map<String, TestCliTool.StructuredOutputRow> indexedInstances = doRemoteAndIndexOutputOn("Id", remote,
                RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        TestCliTool.StructuredOutputRow firstInstanceRow = indexedInstances.get(firstInstanceId);
        assertEquals("unitTestInstance1", firstInstanceRow.get("Name"));
        assertEquals("1", firstInstanceRow.get("Version"));
        assertEquals("DEVELOPMENT", firstInstanceRow.get("Purpose"));

        TestCliTool.StructuredOutputRow secondInstanceRow = indexedInstances.get(secondInstanceId);
        assertEquals("unitTestInstance2", secondInstanceRow.get("Name"));
        assertEquals("1", secondInstanceRow.get("Version"));
        assertEquals("PRODUCTIVE", secondInstanceRow.get("Purpose"));

        /* let's add 1 instance variables for both and 1 variable for just one */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances,
                "--setVariable=commonVar", "--value=23", "--type=NUMERIC", "--customEditor=io.something.editor",
                "--description=my awesome common variable");
        assertEquals(2, result.size());
        assertEquals("Updated successfully", result.get(0).get("Message"));
        assertEquals("Updated successfully", result.get(1).get("Message"));

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + firstInstanceId,
                "--setVariable=customVar", "--value=345");
        assertEquals(1, result.size());
        assertEquals("Updated successfully", result.get(0).get("Message"));

        /* let's try listing variables for both and seeing it's not allowed */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances, "--showVariables");
        assertEquals("Exactly 1 uuid must be provided for listing variables", result.get(0).get("message"));

        /* try listing variables for one */
        Map<String, TestCliTool.StructuredOutputRow> firstInstanceVariables = doRemoteAndIndexOutputOn("Id", remote,
                RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + firstInstanceId, "--showVariables");
        assertEquals(2, firstInstanceVariables.size());
        TestCliTool.StructuredOutputRow commonVariableRow = firstInstanceVariables.get("commonVar");
        assertEquals("my awesome common variable", commonVariableRow.get("Description"));
        assertEquals("NUMERIC", commonVariableRow.get("Type"));
        assertEquals("io.something.editor", commonVariableRow.get("CustomEditor"));
        assertEquals("23", commonVariableRow.get("Value"));

        TestCliTool.StructuredOutputRow customVariableRow = firstInstanceVariables.get("customVar");
        assertEquals("", customVariableRow.get("Description"));
        assertEquals("STRING", customVariableRow.get("Type"));
        assertEquals("", customVariableRow.get("CustomEditor"));
        assertEquals("345", customVariableRow.get("Value"));

        /* let's remove the custom variable and list variables again for each */
        Map<String, TestCliTool.StructuredOutputRow> instanceResult = doRemoteAndIndexOutputOn("Instance", remote,
                RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances, "--removeVariable=customVar");
        assertEquals("Updated successfully", instanceResult.get(firstInstanceId).get("Message"));
        //because it never had the custom variable
        assertEquals("Nothing to modify", instanceResult.get(secondInstanceId).get("Message"));

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + firstInstanceId, "--showVariables");
        assertEquals(1, result.size());
        assertEquals("commonVar", result.get(0).get("Id"));
        assertEquals("my awesome common variable", result.get(0).get("Description"));
        assertEquals("NUMERIC", result.get(0).get("Type"));
        assertEquals("io.something.editor", result.get(0).get("CustomEditor"));
        assertEquals("23", result.get(0).get("Value"));

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + secondInstanceId,
                "--showVariables");
        assertEquals(1, result.size());
        assertEquals("commonVar", result.get(0).get("Id"));
        assertEquals("my awesome common variable", result.get(0).get("Description"));
        assertEquals("NUMERIC", result.get(0).get("Type"));
        assertEquals("io.something.editor", result.get(0).get("CustomEditor"));
        assertEquals("23", result.get(0).get("Value"));

        /* let's delete both instances */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances, "--delete", "--yes");

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        assertEquals(0, result.size());
    }

    @Test
    void testCreateInstanceAndUpdateWithTemplate(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        var product = TestProductFactory.generateProduct();
        // create one template with default values, and one without
        InstanceTemplateDescriptor smallInstanceTemplate = TestProductFactory.generateMinimalInstanceTemplate("Small instance");
        product.instanceTemplates = Map.of("min-instance-template.yaml", smallInstanceTemplate,
                "instance-template.yaml", TestProductFactory.generateInstanceTemplate());
        product.descriptor.instanceTemplates = List.of("min-instance-template.yaml", "instance-template.yaml");

        createInstanceGroup(remote);
        uploadProduct(remote, tmp, Path.of(local.getUri()), product);

        // Create instance template without any values set
        Path templateWithDefaultsPath = tmp.resolve("min-instance-response.yaml");
        InstanceTemplateReferenceDescriptor templateWithDefaults = TestProductFactory.generateInstanceTemplateReference(
                "no overrides instance", smallInstanceTemplate.name);
        templateWithDefaults.autoStart = null;
        templateWithDefaults.autoUninstall = null;
        TestProductFactory.writeToFile(templateWithDefaultsPath, templateWithDefaults);
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_1",
                "--purpose=TEST", "--template=" + templateWithDefaultsPath);

        // Create the instance with overrides
        Path templateWithOverridePath= tmp.resolve("min-instance-response.yaml");
        InstanceTemplateReferenceDescriptor templateWithOverrides = TestProductFactory.generateInstanceTemplateReference();
        templateWithOverrides.autoStart = !product.instanceTemplates.get("instance-template.yaml").autoStart;
        templateWithOverrides.autoUninstall = !product.instanceTemplates.get("instance-template.yaml").autoUninstall;
        TestProductFactory.writeToFile(templateWithOverridePath, templateWithOverrides);
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_2",
                "--purpose=PRODUCTIVE", "--template=" + templateWithOverridePath);

        // Check if the instances was set up correctly
        Map<String, TestCliTool.StructuredOutputRow> output = doRemoteAndIndexOutputOn("Name",
                remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(2, output.size());
        TestCliTool.StructuredOutputRow instanceWithDefaults = output.get("INSTANCE_1");
        assertEquals("Instance From TestProductFactory", instanceWithDefaults.get("Description"));
        assertEquals("1", instanceWithDefaults.get("Version"));
        assertEquals("", instanceWithDefaults.get("Installed"));
        assertEquals("", instanceWithDefaults.get("Active"));
        assertEquals("", instanceWithDefaults.get("AutoStart"));
        assertEquals("*", instanceWithDefaults.get("AutoUninstall"));
        assertEquals("TEST", instanceWithDefaults.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instanceWithDefaults.get("Product"));
        assertEquals("1.0.0", instanceWithDefaults.get("ProductVersion"));
        assertEquals(".*", instanceWithDefaults.get("ProductVerRegex"));
        assertEquals("None", instanceWithDefaults.get("System"));

        TestCliTool.StructuredOutputRow instanceWithOverrides = output.get("INSTANCE_2");
        assertEquals("Instance From TestProductFactory", instanceWithOverrides.get("Description"));
        assertEquals("1", instanceWithOverrides.get("Version"));
        assertEquals("", instanceWithOverrides.get("Installed"));
        assertEquals("", instanceWithOverrides.get("Active"));
        assertEquals("", instanceWithOverrides.get("AutoStart"));
        assertEquals("*", instanceWithOverrides.get("AutoUninstall"));
        assertEquals("PRODUCTIVE", instanceWithOverrides.get("Purpose"));
        assertEquals("io.bdeploy/test/product", instanceWithOverrides.get("Product"));
        assertEquals("1.0.0", instanceWithOverrides.get("ProductVersion"));
        assertEquals(".*", instanceWithOverrides.get("ProductVerRegex"));
        assertEquals("None", instanceWithOverrides.get("System"));

        // create an update template and apply it
        var uuid = instanceWithDefaults.get("Id");
        Path instanceUpdateTemplatePath = tmp.resolve("instance-update-response.yaml");
        InstanceTemplateReferenceDescriptor instanceUpdateTemplate = TestProductFactory.generateInstanceTemplateReference();
        // these values are ignored and the original ones are kept
        instanceUpdateTemplate.autoStart = true;
        instanceUpdateTemplate.autoUninstall = false;
        // replace this so that it doesn't generate an error on update
        SystemTemplateInstanceTemplateGroupMapping mapping = new SystemTemplateInstanceTemplateGroupMapping();
        mapping.group = "Another Group";
        mapping.node = "master";
        instanceUpdateTemplate.defaultMappings = List.of(mapping);
        TestProductFactory.writeToFile(instanceUpdateTemplatePath, instanceUpdateTemplate);

        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=GROUP_NAME", "--uuid=" + uuid, "--version=1", "--activate");
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--update", "--uuid=" + uuid, "--name=UPDATED",
                "--purpose=PRODUCTIVE", "--template=" + instanceUpdateTemplatePath);

        // Check if the instance was updated correctly
        Map<String, TestCliTool.StructuredOutputRow> updatedInstances = doRemoteAndIndexOutputOn(
                row -> row.get("Name") + "-" + row.get("Version"),
                remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list", "--all");
        assertEquals(3, updatedInstances.size());
        TestCliTool.StructuredOutputRow latestVersionRow = updatedInstances.get("INSTANCE_1-2");
        assertEquals("", latestVersionRow.get("Installed"));
        assertEquals("", latestVersionRow.get("Active"));
        assertEquals("", latestVersionRow.get("AutoStart"));
        assertEquals("*", latestVersionRow.get("AutoUninstall"));
        assertEquals("TEST", latestVersionRow.get("Purpose"));
    }

    @Test
    void testMinMinionVersionWithoutTemplate(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        // Create instance group
        createInstanceGroup(remote);

        // Create product
        TestProductDescriptor productDescriptor = TestProductFactory.generateProduct();
        productDescriptor.descriptor.minMinionVersion = "999.0.0";
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        TestProductFactory.writeProductToFile(productPath, productDescriptor);
        uploadProduct(remote, Path.of(local.getUri()), productPath);

        // Run Test
        assertThrows(InternalServerErrorException.class,
                () -> remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_NAME",
                        "--purpose=TEST", "--product=io.bdeploy/test/product", "--productVersion=1.0.0"));
    }

    @Test
    void testMinMinionVersionWithTemplate(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        // Create instance group
        createInstanceGroup(remote);

        // Create product
        TestProductDescriptor productDescriptor = TestProductFactory.generateProduct();
        productDescriptor.descriptor.minMinionVersion = "999.0.0";
        Path productPath = Files.createDirectory(tmp.resolve("product"));
        TestProductFactory.writeProductToFile(productPath, productDescriptor);
        uploadProduct(remote, Path.of(local.getUri()), productPath);

        // Create instance template
        Path responseFilePath = tmp.resolve("ResponseFile.yaml");
        TestProductFactory.writeToFile(responseFilePath, TestProductFactory.generateInstanceTemplateReference());

        // Run Test
        StructuredOutput output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create",
                "--name=INSTANCE_NAME", "--purpose=TEST", "--template=" + responseFilePath);
        assertEquals(1, output.size());
        assertTrue(output.get(0).get("message").startsWith("ERROR: "), "Installation must fail due to minMinionVersion");
    }

    @Test
    void testMinMinionVersionInstanceUpdate(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        // Create instance group
        createInstanceGroup(remote);

        // Create product version 1
        TestProductDescriptor productDescriptor1 = TestProductFactory.generateProduct();
        productDescriptor1.descriptor.minMinionVersion = "0.0.0";

        // Create product version 2
        TestProductDescriptor productDescriptor2 = TestProductFactory.generateProduct();
        productDescriptor2.descriptor.minMinionVersion = "999.0.0";
        productDescriptor2.version = TestProductFactory.generateProductVersion("2.0.0");

        // Upload product versions
        Path bhivePath = Path.of(local.getUri());
        Path productPath1 = Files.createDirectory(tmp.resolve("product1"));
        Path productPath2 = Files.createDirectory(tmp.resolve("product2"));
        TestProductFactory.writeProductToFile(productPath1, productDescriptor1);
        TestProductFactory.writeProductToFile(productPath2, productDescriptor2);
        remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath, "--import=" + productPath1,
                "--push");
        remote(remote, ProductTool.class, "--instanceGroup=GROUP_NAME", "--hive=" + bhivePath, "--import=" + productPath2,
                "--push");
        ProductManifest.invalidateAllScanCaches();

        // Create instance
        remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--create", "--name=INSTANCE_NAME",
                "--purpose=TEST", "--product=io.bdeploy/test/product", "--productVersion=1.0.0");
        TestCliTool.StructuredOutput output = remote(remote, RemoteInstanceTool.class, "--instanceGroup=GROUP_NAME", "--list");
        assertEquals(1, output.size());
        assertEquals("INSTANCE_NAME", output.get(0).get("Name"));
        assertEquals("", output.get(0).get("Description"));
        assertEquals("1", output.get(0).get("Version"));
        assertEquals("", output.get(0).get("Installed"));
        assertEquals("", output.get(0).get("Active"));
        assertEquals("", output.get(0).get("AutoStart"));
        assertEquals("*", output.get(0).get("AutoUninstall"));
        assertEquals("TEST", output.get(0).get("Purpose"));
        assertEquals("io.bdeploy/test/product", output.get(0).get("Product"));
        assertEquals("1.0.0", output.get(0).get("ProductVersion"));
        assertEquals("", output.get(0).get("ProductVerRegex"));
        assertEquals("None", output.get(0).get("System"));

        // Run Test
        assertThrows(InternalServerErrorException.class, () -> remote(remote, RemoteInstanceTool.class,
                "--instanceGroup=GROUP_NAME", "--uuid=" + output.get(0).get("Id"), "--updateTo=2.0.0"));
    }
}

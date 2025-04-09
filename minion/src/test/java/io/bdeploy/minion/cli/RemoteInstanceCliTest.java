package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

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
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;

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
                "--purpose=DEVELOPMENT");

        assertEquals("Success", result.get(0).get("message"));
        assertEquals("DEVELOPMENT", result.get(0).get("NewPurpose"));

        /* update purpose to PRODUCTIVE to create a third instance version */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--update", "--uuid=" + id,
                "--purpose=PRODUCTIVE");

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

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("3", result.get(0).get("Version"));
        assertEquals("", result.get(0).get("Installed")); // not installed
        assertEquals("", result.get(0).get("Active")); // not active
        assertEquals("PRODUCTIVE", result.get(0).get("Purpose"));

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

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        TestCliTool.StructuredOutputRow actualFirstInstanceRow = firstInstanceId.equals(result.get(0).get("Id")) ? result.get(0) : result.get(1);
        assertEquals("unitTestInstance1", actualFirstInstanceRow.get("Name"));
        assertEquals("1", actualFirstInstanceRow.get("Version"));
        assertEquals("DEVELOPMENT", actualFirstInstanceRow.get("Purpose"));

        TestCliTool.StructuredOutputRow actualSecondInstanceRow = secondInstanceId.equals(result.get(0).get("Id")) ? result.get(0) : result.get(1);
        assertEquals("unitTestInstance2", actualSecondInstanceRow.get("Name"));
        assertEquals("1", actualSecondInstanceRow.get("Version"));
        assertEquals("PRODUCTIVE", actualSecondInstanceRow.get("Purpose"));

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
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances,
                "--showVariables");
        assertEquals("Exactly 1 uuid must be provided for listing variables", result.get(0).get("message"));

        /* try listing variables for one */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + firstInstanceId,
                "--showVariables");
        assertEquals(2, result.size());
        TestCliTool.StructuredOutputRow commonVariableRow = "commonVar".equals(result.get(0).get("Id")) ? result.get(0) : result.get(1);
        assertEquals("my awesome common variable", commonVariableRow.get("Description"));
        assertEquals("NUMERIC", commonVariableRow.get("Type"));
        assertEquals("io.something.editor", commonVariableRow.get("CustomEditor"));
        assertEquals("23", commonVariableRow.get("Value"));

        TestCliTool.StructuredOutputRow customVariableRow =  "customVar".equals(result.get(0).get("Id")) ? result.get(0) : result.get(1);
        assertEquals("", customVariableRow.get("Description"));
        assertEquals("STRING", customVariableRow.get("Type"));
        assertEquals("", customVariableRow.get("CustomEditor"));
        assertEquals("345", customVariableRow.get("Value"));

        /* let's remove the custom variable and list variables again for each */
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances,
                "--removeVariable=customVar");
        actualFirstInstanceRow = firstInstanceId.equals(result.get(0).get("Instance")) ? result.get(0) : result.get(1);
        assertEquals("Updated successfully", actualFirstInstanceRow.get("Message"));

        actualSecondInstanceRow = secondInstanceId.equals(result.get(0).get("Instance")) ? result.get(0) : result.get(1);
        assertEquals("Nothing to modify", actualSecondInstanceRow.get("Message")); //because it never had the custom variable

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + firstInstanceId,
                "--showVariables");
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
        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--uuid=" + bothInstances, "--delete",
                "--yes");

        result = remote(remote, RemoteInstanceTool.class, "--instanceGroup=demo", "--list", "--all");
        assertEquals(0, result.size());
    }
}

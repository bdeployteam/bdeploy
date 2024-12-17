package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteInstanceTool;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteInstanceCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp, @AuthPack String auth)
            throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        StructuredOutput result;

        /* At first we have only one version of instance aaa-bbb-ccc */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all");
        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("TEST", result.get(0).get("Purpose"));

        /* update purpose to DEVELOPMENT to create a second instance version */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--update", "--uuid=" + id, "--purpose=DEVELOPMENT");

        assertEquals("Success", result.get(0).get("message"));
        assertEquals("DEVELOPMENT", result.get(0).get("NewPurpose"));

        /* update purpose to PRODUCTIVE to create a third instance version */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--update", "--uuid=" + id, "--purpose=PRODUCTIVE");

        assertEquals("Success", result.get(0).get("message"));
        assertEquals("PRODUCTIVE", result.get(0).get("NewPurpose"));

        /* list --all returns all 3 versions */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all");
        assertEquals(3, result.size());
        assertEquals("3", result.get(0).get("Version"));
        assertEquals("PRODUCTIVE", result.get(0).get("Purpose"));
        assertEquals("2", result.get(1).get("Version"));
        assertEquals("DEVELOPMENT", result.get(1).get("Purpose"));
        assertEquals("1", result.get(2).get("Version"));
        assertEquals("TEST", result.get(2).get("Purpose"));

        /* since there are no active or installed versions, list by default will return latest version (3) */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("3", result.get(0).get("Version"));
        assertEquals("", result.get(0).get("Installed")); // not installed
        assertEquals("", result.get(0).get("Active")); // not active
        assertEquals("PRODUCTIVE", result.get(0).get("Purpose"));

        /* let's install first (1) and second (2) versions */
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=1", "--install");
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=2", "--install");

        /*
         * since there are no active versions, list by default will return latest installed version.
         * since there are only two installed versions: (1) and (2), list will return (2) as it is the latest one
         */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("2", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("", result.get(0).get("Active")); // not active
        assertEquals("DEVELOPMENT", result.get(0).get("Purpose"));

        /* let's activate first (1) version */
        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=1", "--activate");

        /* since there is an active version (1), list by default will return it. */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("*", result.get(0).get("Active")); // active
        assertEquals("TEST", result.get(0).get("Purpose"));

        /* even though we have 3 versions to display, limit = 2 should return only 2 entries */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all", "--limit=2");
        assertEquals(2, result.size());

        /* filter by version */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all", "--version=2");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("2", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("", result.get(0).get("Active")); // active
        assertEquals("DEVELOPMENT", result.get(0).get("Purpose"));

        /** filter by purpose */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all", "--purpose=TEST");

        assertEquals(1, result.size());
        assertEquals("aaa-bbb-ccc", result.get(0).get("Id"));
        assertEquals("DemoInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("*", result.get(0).get("Installed")); // installed
        assertEquals("*", result.get(0).get("Active")); // active
        assertEquals("TEST", result.get(0).get("Purpose"));

        /* let's delete instance */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--delete", "--yes");
        assertEquals("aaa-bbb-ccc", result.get(0).get("Instance"));
        assertEquals("Deleted", result.get(0).get("Result"));

        /* list will return nothing, since instance is deleted */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all");
        assertEquals(0, result.size());

        /* let's create instance with name unitTestInstance */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--create", "--name=unitTestInstance", "--purpose=DEVELOPMENT", "--product=customer/product",
                "--productVersion=1.0.0.1234");
        String createdInstanceId = result.get(0).get("InstanceId");

        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all");
        assertEquals(1, result.size());
        assertEquals(createdInstanceId, result.get(0).get("Id"));
        assertEquals("unitTestInstance", result.get(0).get("Name"));
        assertEquals("1", result.get(0).get("Version"));
        assertEquals("DEVELOPMENT", result.get(0).get("Purpose"));

        /* let's delete instance */
        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + createdInstanceId, "--delete", "--yes");

        result = tools.execute(RemoteInstanceTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--list", "--all");
        assertEquals(0, result.size());
    }
}

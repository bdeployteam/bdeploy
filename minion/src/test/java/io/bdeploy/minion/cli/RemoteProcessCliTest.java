package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import io.bdeploy.ui.cli.RemoteProcessTool;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteProcessCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp, @AuthPack String auth)
            throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--install");

        tools.execute(RemoteDeploymentTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--version=" + instance.getTag(), "--activate");

        StructuredOutput result;
        Exception ex;
        /* must specify only one flag: --list, --start or --stop */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                    "--uuid=" + id);
        });
        assertEquals("Missing --start or --stop or --list", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () -> {
            tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                    "--uuid=" + id, "--start", "--stop");
        });
        assertEquals("You can enable only one flag at a time: --start, --stop or --list", ex.getMessage());

        /* cannot specify --controlGroupName without --controlGroupNodeName */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                    "--uuid=" + id, "--list", "--controlGroupName=Default");
        });
        assertEquals("--controlGroupName cannot be specified without --controlGroupNodeName and vice versa", ex.getMessage());

        /* cannot specify --application and --controlGroupName at the same time */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                    "--uuid=" + id, "--list", "--application=app", "--controlGroupName=Default", "--controlGroupNodeName=master");
        });
        assertEquals("specify either only --application or only --controlGroupName", ex.getMessage());

        /* --join can go with --start/--stop and --application */
        tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--stop", "--join", "--application=app");

        /* needs to be a single application */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                    "--uuid=" + id, "--start", "--join");
        });
        assertEquals("--join is only possible when starting/stopping a single application", ex.getMessage());

        /* list all processes */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--list");
        assertEquals(1, result.size());
        assertEquals("app", result.get(0).get("Id"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("STOPPED", result.get(0).get("Status"));

        /* list by --controlGroupName */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--list", "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals(1, result.size());
        assertEquals("app", result.get(0).get("Id"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("STOPPED", result.get(0).get("Status"));

        /* list by --application */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--list", "--application=app");
        assertEquals(1, result.size());
        assertEquals("Details for app of instance aaa-bbb-ccc of instance group demo", result.get(0).get("message"));
        assertEquals("app", result.get(0).get("ApplicationId"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("STOPPED", result.get(0).get("State"));

        /* start/stop all processes */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--start");
        assertEquals("Success", result.get(0).get("message"));

        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--stop");
        assertEquals("Success", result.get(0).get("message"));

        /* start/stop by --controlGroupName */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--start", "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals("Success", result.get(0).get("message"));

        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--stop", "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals("Success", result.get(0).get("message"));

        /* start/stop by --application */
        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--start", "--application=app");
        assertEquals("Success", result.get(0).get("message"));

        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--start", "--application=app", "--join");
        assertEquals("Success", result.get(0).get("message"));

        result = tools.execute(RemoteProcessTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--stop", "--application=app");
        assertEquals("Success", result.get(0).get("message"));
    }
}

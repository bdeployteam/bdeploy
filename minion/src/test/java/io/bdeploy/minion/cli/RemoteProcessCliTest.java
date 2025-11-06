package io.bdeploy.minion.cli;

import static io.bdeploy.minion.cli.MultiNodeTestActions.attachMultiNodes;
import static io.bdeploy.minion.cli.MultiNodeTestActions.createMultiNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.MultiNodeCompletion;
import io.bdeploy.minion.TestMinion.MultiNodeMaster;
import io.bdeploy.minion.TestMinion.SourceMinion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import io.bdeploy.ui.cli.RemoteProcessTool;

@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteProcessCliTest extends BaseMinionCliTest {

    private static final String MULTINODE_NAME = "multiNode";
    private static final String RUNTIME_NODE_PREFIX = "runtimeNode";

    @RegisterExtension
    private final TestMinion exStandalone = new TestMinion(MinionMode.STANDALONE);
    @RegisterExtension
    private final TestMinion exRuntimeNode1 = new TestMinion(MinionMode.NODE, RUNTIME_NODE_PREFIX + "1", MinionDto.MinionNodeType.MULTI_RUNTIME);
    @RegisterExtension
    private final TestMinion exRuntimeNode2 = new TestMinion(MinionMode.NODE, RUNTIME_NODE_PREFIX + "2", MinionDto.MinionNodeType.MULTI_RUNTIME);

    @SlowTest
    @Test
    void testRemoteCli(BHive local, @SourceMinion(MinionMode.STANDALONE) CommonRootResource common,
            @SourceMinion(MinionMode.STANDALONE) RemoteService remote, @TempDir Path tmp) throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--install");

        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--activate");

        StructuredOutput result;
        Exception ex;
        /* must specify only one flag: --list, --start or --stop */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id);
        });
        assertEquals("Missing --start or --stop or --list", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () -> {
            remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--stop");
        });
        assertEquals("You can enable only one flag at a time: --start, --stop or --list", ex.getMessage());

        /* cannot specify --controlGroupName without --controlGroupNodeName */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list",
                    "--controlGroupName=Default");
        });
        assertEquals("--controlGroupName cannot be specified without --controlGroupNodeName and vice versa", ex.getMessage());

        /* cannot specify --application and --controlGroupName at the same time */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list", "--application=app",
                    "--controlGroupName=Default", "--controlGroupNodeName=master");
        });
        assertEquals("specify either only --application or only --controlGroupName", ex.getMessage());

        /* --join can go with --start/--stop and --application */
        remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop", "--join", "--application=app");

        /* needs to be a single application */
        ex = assertThrows(IllegalArgumentException.class, () -> {
            remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--join");
        });
        assertEquals("--join is only possible when starting/stopping a single application", ex.getMessage());

        /* list all processes */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(1, result.size());
        assertEquals("app", result.get(0).get("Id"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("master", result.get(0).get("Node"));
        assertEquals("STOPPED", result.get(0).get("Status"));

        /* list by --controlGroupName */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list",
                "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals(1, result.size());
        assertEquals("app", result.get(0).get("Id"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("master", result.get(0).get("Node"));
        assertEquals("STOPPED", result.get(0).get("Status"));

        /* list by --application */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list", "--application=app");
        assertEquals(1, result.size());
        assertEquals("Details for app of instance aaa-bbb-ccc of instance group demo", result.get(0).get("message"));
        assertEquals("app", result.get(0).get("ApplicationId"));
        assertEquals("app", result.get(0).get("Name"));
        assertEquals("STOPPED", result.get(0).get("State"));

        /* start/stop all processes */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start");
        assertEquals("Success", result.get(0).get("message"));

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop");
        assertEquals("Success", result.get(0).get("message"));

        /* start/stop by --controlGroupName */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start",
                "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals("Success", result.get(0).get("message"));

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop",
                "--controlGroupName=Default", "--controlGroupNodeName=master");
        assertEquals("Success", result.get(0).get("message"));

        /* start/stop by --application */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--application=app");
        assertEquals("Success", result.get(0).get("message"));

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--application=app",
                "--join");
        assertEquals("Success", result.get(0).get("message"));

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop", "--application=app");
        assertEquals("Success", result.get(0).get("message"));
    }

    @SlowTest
    @Test
    void testRemoteWithMultiNode(BHive local, @SourceMinion(MinionMode.STANDALONE) CommonRootResource common,
            @SourceMinion(MinionMode.STANDALONE) RemoteService remote, @MultiNodeMaster(MULTINODE_NAME) MultiNodeMasterFile masterFile,
            @SourceMinion(value = MinionMode.NODE, disambiguation = RUNTIME_NODE_PREFIX + "1") MultiNodeCompletion runtimeNode1,
            @SourceMinion(value = MinionMode.NODE, disambiguation = RUNTIME_NODE_PREFIX + "2") MultiNodeCompletion runtimeNode2,
            @TempDir Path tmp) throws IOException, InterruptedException {
        createMultiNode(remote, MULTINODE_NAME);

        Manifest.Key instance = TestFactory.createApplicationsAndInstanceOnMultiNode(local, common, remote, tmp, MULTINODE_NAME);
        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--install");
        remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                "--activate");

        StructuredOutput result;

        /* Start without specifying the runtime node */
        remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--join", "--application=app");
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(0, result.size());

        // attaching nodes and listing
        attachMultiNodes(remote, masterFile, runtimeNode1, runtimeNode2);

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--application=app");
        assertEquals("Success", result.get(0).get("message"));

        Map<String, TestCliTool.StructuredOutputRow> processRows = doRemoteAndIndexOutputOn("Node", remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(2, processRows.size());
        TestCliTool.StructuredOutputRow processRow = processRows.get("multiNode/NODE-multi-runtimeNode1");
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertTrue("RUNNING_NOT_STARTED".equals(processRow.get("Status")) || "RUNNING".equals(processRow.get("Status")));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertNotEquals("-", processRow.get("StartedAt"));
        assertNotEquals("-", processRow.get("OsUser"));
        assertNotEquals("-", processRow.get("Pid"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        processRow = processRows.get("multiNode/NODE-multi-runtimeNode2");
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertTrue("RUNNING_NOT_STARTED".equals(processRow.get("Status")) || "RUNNING".equals(processRow.get("Status")));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertNotEquals("-", processRow.get("StartedAt"));
        assertNotEquals("-", processRow.get("OsUser"));
        assertNotEquals("-", processRow.get("Pid"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        /* Stop without specifying the runtime node */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop", "--application=app");
        assertEquals("Success", result.get(0).get("message"));

        processRows = doRemoteAndIndexOutputOn("Node", remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(2, processRows.size());
        processRow = processRows.get("multiNode/NODE-multi-runtimeNode1");
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertEquals("STOPPED", processRow.get("Status"));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertEquals("-", processRow.get("StartedAt"));
        assertEquals("-", processRow.get("OsUser"));
        assertEquals("-", processRow.get("Pid"));
        assertNotEquals("", processRow.get("ExitCode"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        processRow = processRows.get("multiNode/NODE-multi-runtimeNode2");
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertEquals("STOPPED", processRow.get("Status"));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertEquals("-", processRow.get("StartedAt"));
        assertEquals("-", processRow.get("OsUser"));
        assertEquals("-", processRow.get("Pid"));
        assertNotEquals("", processRow.get("ExitCode"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        /* Start and stop on a single specified node */
        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--start", "--application=app", "--nodeName=NODE-multi-runtimeNode2");
        assertEquals("Success", result.get(0).get("message"));

        processRows = doRemoteAndIndexOutputOn("Node", remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(2, processRows.size());
        processRow = processRows.get("multiNode/NODE-multi-runtimeNode1");
        assertEquals("STOPPED", processRow.get("Status"));
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertEquals("STOPPED", processRow.get("Status"));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertEquals("-", processRow.get("StartedAt"));
        assertEquals("-", processRow.get("OsUser"));
        assertEquals("-", processRow.get("Pid"));
        assertNotEquals("", processRow.get("ExitCode"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        processRow = processRows.get("multiNode/NODE-multi-runtimeNode2");
        assertEquals("app", processRow.get("Id"));
        assertEquals("app", processRow.get("Name"));
        assertTrue("RUNNING_NOT_STARTED".equals(processRow.get("Status")) || "RUNNING".equals(processRow.get("Status")));
        assertEquals("1", processRow.get("Version"));
        assertEquals("1.0.0.1234", processRow.get("ProductVersion"));
        assertEquals("MANUAL", processRow.get("StartType"));
        assertNotEquals("-", processRow.get("StartedAt"));
        assertNotEquals("-", processRow.get("OsUser"));
        assertNotEquals("-", processRow.get("Pid"));
        assertEquals("", processRow.get("StartupStatus"));
        assertEquals("", processRow.get("LivenessStatus"));

        result = remote(remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--stop", "--application=app", "--nodeName=NODE-multi-runtimeNode2");
        assertEquals("Success", result.get(0).get("message"));

        processRows = doRemoteAndIndexOutputOn("Node", remote, RemoteProcessTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
        assertEquals(2, processRows.size());
        assertEquals("STOPPED", processRows.get("multiNode/NODE-multi-runtimeNode1").get("Status"));
        assertEquals("STOPPED", processRows.get("multiNode/NODE-multi-runtimeNode2").get("Status"));
    }
}

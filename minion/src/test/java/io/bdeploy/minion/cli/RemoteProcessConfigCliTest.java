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
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteProcessConfigTool;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteProcessConfigCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp, @AuthPack String auth)
            throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* Check list, add, set, remove of process parameters */
        var result = tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--process=app", "--showParameters");

        assertEquals(2, result.size());
        assertEquals("sleepParam", result.get(0).get("Id"));
        assertEquals("", result.get(0).get("Value"));
        assertEquals("XENV", result.get(1).get("Id"));
        assertEquals("Value", result.get(1).get("Value"));

        tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--process=app", "--set=--param1", "--value=TestValue");

        result = tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--process=app", "--showParameters");

        assertEquals(3, result.size());
        assertEquals("sleepParam", result.get(0).get("Id"));
        assertEquals("", result.get(0).get("Value"));
        assertEquals("--param1", result.get(1).get("Id"));
        assertEquals("TestValue", result.get(1).get("Value"));
        assertEquals("XENV", result.get(2).get("Id"));
        assertEquals("Value", result.get(2).get("Value"));

        tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--process=app", "--set=--param1", "--value=Other Test Value");

        result = tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--process=app", "--showParameters");

        assertEquals(3, result.size());
        assertEquals("--param1", result.get(1).get("Id"));
        assertEquals("Other Test Value", result.get(1).get("Value"));

        tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--process=app", "--remove=--param1");

        result = tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--process=app", "--showParameters");

        assertEquals(2, result.size());
        assertEquals("sleepParam", result.get(0).get("Id"));
        assertEquals("XENV", result.get(1).get("Id"));

        tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--process=app", "--set=my.custom.1", "--value=Custom One");

        tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--instanceGroup=demo",
                "--uuid=" + id, "--process=app", "--set=my.custom.2", "--value=Custom Two", "--predecessor=sleepParam");

        result = tools.execute(RemoteProcessConfigTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--instanceGroup=demo", "--uuid=" + id, "--process=app", "--showParameters");

        assertEquals(4, result.size());
        assertEquals("my.custom.1", result.get(0).get("Id"));
        assertEquals("Custom One", result.get(0).get("Value"));
        assertEquals("*", result.get(0).get("Custom"));
        assertEquals("sleepParam", result.get(1).get("Id"));
        assertEquals("", result.get(1).get("Value"));
        assertEquals("", result.get(1).get("Custom"));
        assertEquals("my.custom.2", result.get(2).get("Id"));
        assertEquals("Custom Two", result.get(2).get("Value"));
        assertEquals("*", result.get(2).get("Custom"));
    }
}

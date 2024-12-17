package io.bdeploy.minion.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.cli.BaseMinionCliTest;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteCentralTool;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool;

/**
 * Hello world with CLI test.
 */
class CentralManagedConnectionCliTest extends BaseMinionCliTest {

    @RegisterExtension
    TestMinion centralMinion = new TestMinion(MinionMode.CENTRAL);

    @RegisterExtension
    TestMinion managedMinion = new TestMinion(MinionMode.MANAGED);

    @Test
    void testWithCli(@TempDir Path tmp) {
        RemoteService centralRemote = centralMinion.getRemoteService();
        RemoteService managedRemote = managedMinion.getRemoteService();
        URI centralUri = centralRemote.getUri();
        URI managedUri = managedRemote.getUri();
        String centralAuth = centralRemote.getAuthPack();
        String managedAuth = managedRemote.getAuthPack();

        // create instance group on the central and assert its existence.
        remote(centralUri, centralAuth, RemoteInstanceGroupTool.class, "--create=Test", "--title=TestTitle");
        var output = remote(centralUri, centralAuth, RemoteInstanceGroupTool.class, "--list");

        assertEquals("Test", output.get(0).get("Name"));
        assertEquals("TestTitle", output.get(0).get("Title"));

        // download managed identification file from managed server.
        Path managedIdentFile = tmp.resolve("managed-ident.txt").toAbsolutePath();
        remote(managedUri, managedAuth, RemoteCentralTool.class, "--managedIdent", "--output=" + managedIdentFile);

        assertTrue(PathHelper.exists(managedIdentFile));

        // attach managed server to central using ident file.
        remote(centralUri, centralAuth, RemoteCentralTool.class, "--attach=" + managedIdentFile, "--name=Managed",
                "--instanceGroup=Test", "--description=Test Managed Server");
        output = remote(centralUri, centralAuth, RemoteCentralTool.class, "--list", "--instanceGroup=Test");

        assertEquals("Managed", output.get(0).get("Name"));
        assertEquals("Test Managed Server", output.get(0).get("Description"));

        // check if the instance group was created on the managed server by the attach
        output = remote(managedUri, managedAuth, RemoteInstanceGroupTool.class, "--list");
        assertEquals("Test", output.get(0).get("Name"));
        assertEquals("TestTitle", output.get(0).get("Title"));
    }
}

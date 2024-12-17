package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteRepoTool;
import jakarta.ws.rs.WebApplicationException;

@ExtendWith(TestMinion.class)
class RepoCliTest extends BaseMinionCliTest {

    @Test
    void testCrud(CommonRootResource master, MinionRoot root) {
        assertTrue(master.getSoftwareRepositories().isEmpty());

        SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryConfiguration();
        cfg.name = "test";
        cfg.description = "desc";

        // throws due to wrong storage location
        assertThrows(WebApplicationException.class, () -> master.addSoftwareRepository(cfg, "/some/storage"));

        master.addSoftwareRepository(cfg, root.getStorageLocations().get(0).toString());

        List<SoftwareRepositoryConfiguration> repos = master.getSoftwareRepositories();
        assertEquals(1, repos.size());
        assertEquals("test", repos.iterator().next().name);
        assertEquals("desc", repos.iterator().next().description);
    }

    @Test
    void testToolCreate(CommonRootResource master, RemoteService remote, @AuthPack String auth, MinionRoot root)
            throws IOException {
        tools.execute(RemoteRepoTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--storage=" + root.getStorageLocations().get(0).toString(), "--add=test", "--description=desc");

        tools.execute(RemoteRepoTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--list");

        List<SoftwareRepositoryConfiguration> repos = master.getSoftwareRepositories();
        assertEquals(1, repos.size());
        assertEquals("test", repos.iterator().next().name);
        assertEquals("desc", repos.iterator().next().description);
    }
}

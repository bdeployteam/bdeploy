package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;

@ExtendWith(TestMinion.class)
public class RepoCliTest {

    @RegisterExtension
    TestCliTool tools = new TestCliTool(new MinionServerCli());

    @Test
    public void crud(MasterRootResource master, MinionRoot root) {
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
    public void toolCreate(MasterRootResource master, RemoteService service, @AuthPack String auth, MinionRoot root) {
        tools.getTool(RepoTool.class, "--remote=" + service.getUri(), "--token=" + auth,
                "--storage=" + root.getStorageLocations().get(0).toString(), "--add=test", "--description=desc").run();

        tools.getTool(RepoTool.class, "--remote=" + service.getUri(), "--token=" + auth, "--list").run();

        List<SoftwareRepositoryConfiguration> repos = master.getSoftwareRepositories();
        assertEquals(1, repos.size());
        assertEquals("test", repos.iterator().next().name);
        assertEquals("desc", repos.iterator().next().description);
    }

}

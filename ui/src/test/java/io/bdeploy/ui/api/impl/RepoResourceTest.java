package io.bdeploy.ui.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.ui.TestUiBackendServer;
import io.bdeploy.ui.api.SoftwareRepositoryResource;

@ExtendWith(TestUiBackendServer.class)
public class RepoResourceTest {

    @Test
    void crud(SoftwareRepositoryResource repos) {
        assertTrue(repos.list().isEmpty());

        SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryConfiguration();
        cfg.name = "demo";
        cfg.description = "description";

        repos.create(cfg);

        List<SoftwareRepositoryConfiguration> list = repos.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).name);
        assertEquals("description", list.get(0).description);

        cfg.description = "other";
        repos.update("demo", cfg);

        list = repos.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).name);
        assertEquals("other", list.get(0).description);

        // name mismatch
        assertThrows(WebApplicationException.class, () -> repos.update("test", cfg));

        SoftwareRepositoryConfiguration read = repos.read("demo");
        assertEquals("demo", read.name);
        assertEquals("other", read.description);

        repos.delete("demo");

        assertTrue(repos.list().isEmpty());
    }

}

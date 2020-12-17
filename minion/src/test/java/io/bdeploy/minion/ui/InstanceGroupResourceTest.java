package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;

@ExtendWith(TestMinion.class)
public class InstanceGroupResourceTest {

    @Test
    void crud(InstanceGroupResource res, JerseyClientFactory factory) throws IOException {
        assertTrue(res.list().isEmpty());

        InstanceGroupConfiguration cfg = new InstanceGroupConfiguration();
        cfg.name = "demo";
        cfg.title = "Demo Title";
        cfg.description = "This is a Demo";

        res.create(cfg);

        List<InstanceGroupConfiguration> list = res.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).name);
        assertEquals("Demo Title", list.get(0).title);
        assertEquals("This is a Demo", list.get(0).description);
        assertNull(list.get(0).logo);

        // cannot call update image directly to properly fake a form.
        try (InputStream logoStream = InstanceGroupResourceTest.class.getClassLoader().getResourceAsStream("logo64.png")) {
            MultiPart mp = new MultiPart();
            StreamDataBodyPart bp = new StreamDataBodyPart("image", logoStream);
            bp.setFilename("logo.png");
            bp.setMediaType(new MediaType("image", "png"));
            mp.bodyPart(bp);

            WebTarget target = factory.getBaseTarget().path("/group/demo/image");
            Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

            assertEquals(204, response.getStatus());
        }

        InstanceGroupConfiguration read = res.read("demo");
        assertEquals("demo", read.name);
        assertNotNull(read.logo);

        read.description = "Other description";
        res.update("demo", read);

        InstanceGroupConfiguration reread = res.read("demo");
        assertEquals("Other description", reread.description);
        assertEquals(read.logo, reread.logo);

        res.delete("demo");
        assertTrue(res.list().isEmpty());
    }

}

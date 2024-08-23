package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.InstanceGroupResource;

@ExtendWith(TestMinion.class)
class InstanceGroupResourceTest {

    @Test
    void testCrud(InstanceGroupResource res, JerseyClientFactory factory) throws IOException {
        assertTrue(res.list().isEmpty());

        InstanceGroupConfiguration cfg = new InstanceGroupConfiguration();
        cfg.name = "demo";
        cfg.title = "Demo Title";
        cfg.description = "This is a Demo";

        res.create(cfg);

        List<InstanceGroupConfigurationDto> list = res.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).instanceGroupConfiguration.name);
        assertEquals("Demo Title", list.get(0).instanceGroupConfiguration.title);
        assertEquals("This is a Demo", list.get(0).instanceGroupConfiguration.description);
        assertNull(list.get(0).instanceGroupConfiguration.logo);

        // cannot call update image directly to properly fake a form.
        try (InputStream logoStream = InstanceGroupResourceTest.class.getClassLoader().getResourceAsStream("logo64.png");
                FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("image", logoStream)) {
            res.updateImage("demo", fdmp);
        }

        InstanceGroupConfiguration read = res.getInstanceGroupConfigurationDto("demo").instanceGroupConfiguration;
        assertEquals("demo", read.name);
        assertNotNull(read.logo);

        read.description = "Other description";
        res.update("demo", read);

        InstanceGroupConfiguration reread = res.getInstanceGroupConfigurationDto("demo").instanceGroupConfiguration;
        assertEquals("Other description", reread.description);
        assertEquals(read.logo, reread.logo);

        res.delete("demo");
        assertTrue(res.list().isEmpty());
    }

}

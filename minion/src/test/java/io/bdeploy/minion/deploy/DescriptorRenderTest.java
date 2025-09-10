package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestHive.class)
class DescriptorRenderTest {

    @Test
    void testRemoteDeploy(BHive local, @TempDir Path tmp) throws IOException {

        // the easiest way to get some test data, although it is a little overkill.
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, null, null, tmp, false);
        InstanceManifest imf = InstanceManifest.of(local, instance);

        InstanceNodeManifest inmf = InstanceNodeManifest.of(local, imf.getInstanceNodeManifestKeys().get(Minion.DEFAULT_NAME));
        ApplicationConfiguration app = inmf.getConfiguration().applications.get(0);

        ProcessConfiguration pc = app.renderDescriptor(new VariableResolver() {

            @Override
            public String apply(String t) {
                return ""; // ignore all variables, not goal of this test.
            }
        });

        assertEquals(2, pc.start.size());
        assertTrue(pc.start.get(0).contains("launch.")); // platform dependent ending
        assertEquals("10", pc.start.get(1));
        assertEquals(1, pc.startEnv.size());
        assertEquals("Value", pc.startEnv.get("XENV"));
    }

}

package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.dto.ClientApplicationDto;
import io.bdeploy.ui.dto.InstanceClientAppsDto;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
public class InstanceGroupResourceTest {

    private static final String GROUP_NAME = "demo";

    @Test
    public void testListClientApps(InstanceGroupResource resource, BHive local, MasterRootResource root,
            CommonRootResource common, RemoteService remote, @TempDir Path tmp) throws IOException, InterruptedException {
        OperatingSystem runningOs = OsHelper.getRunningOs();

        // Create install a small demo instance
        Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);
        MasterNamedResource master = root.getNamedMaster(GROUP_NAME);
        master.install(instance);

        // Non-activated versions should not be contained in the result
        Collection<InstanceClientAppsDto> clientApps = resource.listClientApps(GROUP_NAME, runningOs);
        assertEquals(0, clientApps.size());

        // Activate and check if we now get the desired result
        master.activate(instance);
        clientApps = resource.listClientApps(GROUP_NAME, runningOs);
        assertEquals(1, clientApps.size());

        // A single application for the current OS must be contained
        InstanceClientAppsDto instanceApps = clientApps.iterator().next();
        assertNotNull(instanceApps.instance);
        assertEquals(1, instanceApps.applications.size());

        ClientApplicationDto app = instanceApps.applications.iterator().next();
        assertEquals(OsHelper.getRunningOs(), app.os);
        assertEquals("client", app.description);
    }

}

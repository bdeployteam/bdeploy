package io.bdeploy.minion.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

import javax.ws.rs.NotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.EndpointsConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi.InstancePurposeApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.CleanupResource;

/**
 * Tests V1 public API
 */
@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class PublicApiTestV1 {

    @Test
    void testV1(BHive local, MasterRootResource master, CommonRootResource common, CleanupResource cr, RemoteService remote,
            @TempDir Path tmp, ActivityReporter reporter, MinionRoot mr) throws Exception {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true, 1111);

        PublicRootResource prr = ResourceProvider.getResource(remote, PublicRootResource.class, null);

        List<InstanceGroupConfigurationApi> igs = prr.getInstanceGroups();
        assertEquals(1, igs.size());
        assertEquals("demo", igs.get(0).name);
        assertEquals("title", igs.get(0).title);
        assertEquals("For Unit Test", igs.get(0).description);

        SoftwareRepositoryConfiguration src = new SoftwareRepositoryConfiguration();
        src.name = "sr";
        src.description = "Repo";

        common.addSoftwareRepository(src, null);

        List<SoftwareRepositoryConfigurationApi> srs = prr.getSoftwareRepositories();
        assertEquals(1, srs.size());
        assertEquals("sr", srs.get(0).name);
        assertEquals("Repo", srs.get(0).description);

        PublicInstanceResource pir = prr.getInstanceResource("demo");

        SortedMap<Key, InstanceConfigurationApi> ics = pir.listInstanceConfigurations(true);
        assertEquals(instance, ics.firstKey());

        InstanceConfigurationApi ica = ics.get(ics.firstKey());
        assertEquals("aaa-bbb-ccc", ica.uuid);
        assertEquals("DemoInstance", ica.name);
        assertEquals("Demo Instance", ica.description);
        assertEquals(InstancePurposeApi.TEST, ica.purpose);
        assertEquals(new Manifest.Key("customer/product", "1.0.0.1234"), ica.product);

        assertThrows(NotFoundException.class, () -> pir.getAllEndpoints(ica.uuid));

        master.getNamedMaster("demo").install(instance);
        master.getNamedMaster("demo").activate(instance);

        SortedMap<String, EndpointsConfigurationApi> eps = pir.getAllEndpoints(ica.uuid);
        assertTrue(eps.containsKey("app"));

        EndpointsConfigurationApi eca = eps.get("app");
        assertEquals(eca.http.size(), 1);
        assertEquals("test", eca.http.get(0).id);
        assertEquals("/api/test/with/path", eca.http.get(0).path);

    }

}

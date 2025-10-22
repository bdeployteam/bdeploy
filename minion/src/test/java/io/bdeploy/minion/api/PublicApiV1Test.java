package io.bdeploy.minion.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.RemoteDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.api.remote.v1.PublicInstanceResource;
import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.api.remote.v1.dto.EndpointsConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi.InstancePurposeApi;
import io.bdeploy.api.remote.v1.dto.InstanceGroupConfigurationApi;
import io.bdeploy.api.remote.v1.dto.SoftwareRepositoryConfigurationApi;
import io.bdeploy.api.validation.v1.ProductValidationHelper;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.ProductDto;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Tests V1 public API
 */
@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class PublicApiV1Test {

    @Test
    void testV1(BHive local, MasterRootResource master, CommonRootResource common, RemoteService remote, @TempDir Path tmp)
            throws Exception {
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
        assertEquals(new Manifest.Key("customer" + ProductManifestBuilder.PRODUCT_KEY_SUFFIX, "1.0.0.1234"), ica.product);

        assertThrows(NotFoundException.class, () -> pir.getAllEndpoints(ica.uuid));

        master.getNamedMaster("demo").install(instance);
        master.getNamedMaster("demo").activate(instance, false);

        SortedMap<String, EndpointsConfigurationApi> eps = pir.getAllEndpoints(ica.uuid);
        assertTrue(eps.containsKey("app"));

        EndpointsConfigurationApi eca = eps.get("app");
        assertEquals(1, eca.http.size());
        assertEquals("test", eca.http.get(0).id);
        assertEquals("/api/test/with/path", eca.http.get(0).path);

        assertThrows(BadRequestException.class, () -> prr.getInstanceGroupByInstanceId(null));
        assertThrows(BadRequestException.class, () -> prr.getInstanceGroupByInstanceId(""));
        assertThrows(BadRequestException.class, () -> prr.getInstanceGroupByInstanceId("  "));
        assertThrows(NotFoundException.class, () -> prr.getInstanceGroupByInstanceId("Some non-existent instance uuid"));

        InstanceGroupConfigurationApi instanceGroupForInstanceId = prr.getInstanceGroupByInstanceId(ica.uuid);
        assertEquals("demo", instanceGroupForInstanceId.name);
        assertEquals("title", instanceGroupForInstanceId.title);
        assertEquals("For Unit Test", instanceGroupForInstanceId.description);
    }

    @Test
    void testProductsV1(BHive local, CommonRootResource common, InstanceGroupResource igr, RemoteService remote,
            ActivityReporter reporter, @TempDir Path tmp) throws Exception {
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp.resolve("jdk"));

        ScopedManifestKey jdkKey = new ScopedManifestKey("jdk", OsHelper.getRunningOs(), "1.8.0");

        try (Transaction t = local.getTransactions().begin()) {
            local.execute(new ImportOperation().setSourcePath(jdk).setManifest(jdkKey.getKey()));
        }

        SoftwareRepositoryConfiguration src = new SoftwareRepositoryConfiguration();
        src.name = "SW";
        src.description = "SW";

        common.addSoftwareRepository(src, null);

        local.execute(new PushOperation().addManifest(jdkKey.getKey()).setHiveName("SW").setRemote(remote));
        local.execute(new ManifestDeleteOperation().setToDelete(jdkKey.getKey()));

        Path prod = tmp.resolve("product");

        Path pathToApp = TestAppFactory.createDummyApp("myApp", prod);
        Path prodInfo = prod.resolve("product-info.yaml");
        Path prodVer = prod.resolve("product-versions.yaml");

        Files.write(prodInfo, List.of("name: Test Product", "product: com.example/product", "vendor: Unit Test", "applications:",
                " - myApp", "versionFile: product-versions.yaml"));

        String appPath = pathToApp.toString().replace('\\', '/');
        Files.write(prodVer,
                List.of("version: \"1.0.0\"", "appInfo:", " myApp:", "  " + OsHelper.getRunningOs() + ": \"" + appPath + "\""));

        Key prodKey = ProductManifestBuilder.importFromDescriptor(prodInfo, local,
                new RemoteDependencyFetcher(remote, "SW", reporter), false);
        assertNotNull(prodKey);

        Files.write(prodVer,
                List.of("version: \"1.0.1\"", "appInfo:", " myApp:", "  " + OsHelper.getRunningOs() + ": \"" + appPath + "\""));

        // validate product.
        Path valDesc = prod.resolve("product-validation.yaml");
        Files.write(valDesc, List.of("product: product-info.yaml", "applications:",
                " myApp: " + pathToApp.resolve(ApplicationDescriptorApi.FILE_NAME).toString().replace('\\', '/')));
        ProductValidationResponseApi resp = ProductValidationHelper.validate(valDesc, remote);
        for (var i : resp.issues) {
            System.err.println("Issue: " + i.severity + ": " + i.message);
        }
        assertEquals(0, resp.issues.size());

        // try with parallel import as well, even though this does not have much impact with one application to cover the code path.
        Key prod2Key = ProductManifestBuilder.importFromDescriptor(prodInfo, local, new LocalDependencyFetcher(), true);
        assertNotNull(prod2Key);

        InstanceGroupConfiguration cfg = new InstanceGroupConfiguration();
        cfg.name = "IG";
        cfg.description = "IG";

        common.addInstanceGroup(cfg, null);

        local.execute(new PushOperation().addManifest(prodKey).addManifest(prod2Key).setHiveName("IG").setRemote(remote));

        ProductResource pr = igr.getProductResource("IG");
        List<ProductDto> list = pr.list(null);

        assertEquals(2, list.size());

        assertEquals("Test Product", list.get(0).name);
        assertEquals("Test Product", list.get(1).name);
        assertEquals("Unit Test", list.get(0).vendor);
        assertEquals("Unit Test", list.get(1).vendor);
    }

}

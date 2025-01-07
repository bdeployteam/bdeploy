package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.cli.BHiveCli;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveLocator;
import io.bdeploy.bhive.remote.jersey.BHiveResource;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.pcu.TestAppFactory;

@ExtendWith(TestMinion.class)
@ExtendWith(TestActivityReporter.class)
class ProductCliTest extends BaseMinionCliTest {

    @RegisterExtension
    TestCliTool hiveTools = new TestCliTool(new BHiveCli());

    @Test
    void testProductImportWithExtDep(CommonRootResource master, RemoteService remote, MinionRoot root, @TempDir Path temp,
            ActivityReporter reporter, @AuthPack String auth) throws IOException {
        // add a repository
        SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryConfiguration();
        cfg.name = "ext";
        cfg.description = "desc";
        master.addSoftwareRepository(cfg, root.getStorageLocations().get(0).toString());

        // create/push external software from temp hive.
        ScopedManifestKey smk = new ScopedManifestKey("external-dep", OsHelper.getRunningOs(), "1.0.0");
        try (BHive tmpHive = new BHive(temp.resolve("src-hive").toUri(), null, reporter)) {
            Path src = temp.resolve("src");
            PathHelper.mkdirs(src);

            Files.writeString(src.resolve("ext.txt"), "This is demo content");

            try (Transaction t = tmpHive.getTransactions().begin()) {
                tmpHive.execute(new ImportOperation().setManifest(smk.getKey()).setSourcePath(src));
            }
            tmpHive.execute(new PushOperation().addManifest(smk.getKey()).setRemote(remote).setHiveName("ext"));
        }

        // check ext software
        BHiveResource extHive = ResourceProvider.getResource(remote, BHiveLocator.class, null).getNamedHive("ext");
        SortedMap<Key, ObjectId> inventory = extHive.getManifestInventory("external-dep");

        assertEquals(1, inventory.size());
        assertEquals(smk.getKey(), inventory.firstKey());

        // generate an application with app-info.yaml
        Path appPath = TestAppFactory.createDummyApp("app", temp);

        // add a dependency to the app-info.yaml...
        ApplicationDescriptor appInfo;
        Path appInfoYaml = appPath.resolve(ApplicationDescriptorApi.FILE_NAME);
        try (InputStream is = Files.newInputStream(appInfoYaml)) {
            appInfo = StorageHelper.fromYamlStream(is, ApplicationDescriptor.class);
        }
        appInfo.runtimeDependencies.clear();
        appInfo.runtimeDependencies.add("external-dep:1.0.0");
        Files.write(appInfoYaml, StorageHelper.toRawYamlBytes(appInfo));

        // now generate product which uses ext software
        ProductDescriptor pd = new ProductDescriptor();
        ProductVersionDescriptor pvd = new ProductVersionDescriptor();

        Path pdFile = temp.resolve("product-info.yaml");
        Path pvdFile = temp.resolve("product-version.yaml");

        pd.name = "prod";
        pd.product = "prod";
        pd.versionFile = "product-version.yaml";
        pd.applications.add("app");

        pvd.version = "1.0.0";
        pvd.appInfo.put("app", Collections.singletonMap(OsHelper.getRunningOs(), appInfoYaml.toAbsolutePath().toString()));

        Files.write(pdFile, StorageHelper.toRawYamlBytes(pd));
        Files.write(pvdFile, StorageHelper.toRawYamlBytes(pvd));

        // now import the product into a new hive, ext dependency should be fetched from remote
        Path impHive = temp.resolve("imp-hive");
        hiveTools.execute(InitTool.class, "--hive=" + impHive);
        remote(remote.getUri(), auth, ProductTool.class, "--hive=" + impHive, "--import=" + pdFile);

        // this is the key we expect to be created.
        Manifest.Key prodKey = new Manifest.Key("prod" + ProductManifestBuilder.PRODUCT_KEY_SUFFIX, "1.0.0");

        // check whether things went well...
        try (BHive hive = new BHive(impHive.toUri(), null, reporter)) {
            Set<Key> mfs = hive.execute(new ManifestListOperation());

            ScopedManifestKey appSmk = new ScopedManifestKey("prod/app", OsHelper.getRunningOs(), "1.0.0");

            assertTrue(mfs.contains(smk.getKey()));
            assertTrue(mfs.contains(appSmk.getKey()));
            assertTrue(mfs.contains(prodKey));

            // create instance group and push it there, just to test :)
            InstanceGroupConfiguration igc = new InstanceGroupConfiguration();
            igc.name = "test";
            igc.description = "test";

            master.addInstanceGroup(igc, root.getStorageLocations().get(0).toString());
            hive.execute(new PushOperation().addManifest(prodKey).setRemote(remote).setHiveName("test"));
        }
    }
}

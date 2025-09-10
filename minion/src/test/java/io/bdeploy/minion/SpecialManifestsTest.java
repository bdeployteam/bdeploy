package io.bdeploy.minion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestHive.class)
class SpecialManifestsTest {

    @Test
    void testCustomerManifest(BHive hive) {
        InstanceGroupManifest cm = new InstanceGroupManifest(hive);

        assertNull(cm.read(), "Customer Descriptor should not yet be present");

        InstanceGroupConfiguration td = new InstanceGroupConfiguration();
        td.name = "Test with spaces";
        td.description = "Description with spaces";

        cm.update(td);

        assertNotNull(cm.read(), "Customer Descriptor should not be null");
        assertEquals(cm.read().name, td.name);
        assertEquals(cm.read().description, td.description);

        td.name = "New Name";
        td.description = "New Description";

        cm.update(td);

        assertEquals(cm.read().name, td.name);
        assertEquals(cm.read().description, td.description);
    }

    @Test
    void testProductAndApplicationManifest(BHive hive, @TempDir Path tmp) {
        Path app = TestAppFactory.createDummyApp("dummy", tmp);
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("app", OsHelper.getRunningOs()), "1.0");

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setSourcePath(app).setManifest(appKey));
        }

        Manifest.Key prodKey = new Manifest.Key("prod", "1.0");
        ProductDescriptor pd = new ProductDescriptor();
        pd.name = "Dummy Product";
        pd.product = "prod";
        pd.applications.add("app");
        ProductManifestBuilder pmb = new ProductManifestBuilder(pd);
        pmb.add(appKey);
        pmb.insert(hive, prodKey, "Test Product");

        assertEquals("Test Product", ProductManifest.of(hive, prodKey).getProduct());

        ProductManifest pm = ProductManifest.of(hive, prodKey);
        assertNotNull(pm);
        assertEquals(1, pm.getApplications().size());
        assertEquals(appKey, pm.getApplications().first());

        ApplicationManifest am = ApplicationManifest.of(hive, appKey, null);
        assertEquals("dummy", am.getDescriptor().name);
    }

    @Test
    void testInstanceManifest(BHive hive, @TempDir Path tmp) throws IOException {
        String id = UuidHelper.randomId();
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("app", OsHelper.getRunningOs()), "1.0");
        Path app = TestAppFactory.createDummyApp("dummy", tmp);

        Manifest.Key jdkKey = new Manifest.Key(ScopedManifestKey.createScopedName("jdk", OsHelper.getRunningOs()), "1.8.0");
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        { // prepare test data
            try (Transaction t = hive.getTransactions().begin()) {
                hive.execute(new ImportOperation().setSourcePath(app).setManifest(appKey));
                hive.execute(new ImportOperation().setSourcePath(jdk).setManifest(jdkKey));
            }
            ApplicationDescriptor desc = ApplicationManifest.of(hive, appKey, null).getDescriptor();

            Path cfgs = tmp.resolve("config-templates");
            PathHelper.mkdirs(cfgs);
            Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

            Manifest.Key prodKey = new Manifest.Key("prod", "v1");
            ProductDescriptor pd = new ProductDescriptor();
            pd.name = "Dummy Product";
            pd.product = "prod";
            pd.applications.add("app");
            pd.configTemplates = "config-templates";
            new ProductManifestBuilder(pd).add(appKey).setConfigTemplates(cfgs).insert(hive, prodKey, "Dummy Product");

            InstanceNodeManifest.Builder ifmb = new InstanceNodeManifest.Builder();

            ApplicationConfiguration appCfg = new ApplicationConfiguration();
            appCfg.application = appKey;
            appCfg.name = "My Dummy";
            appCfg.start = new CommandConfiguration();
            appCfg.start.executable = desc.startCommand.launcherPath;

            ParameterConfiguration pcfg = new ParameterConfiguration();
            pcfg.id = "--param2";
            pcfg.value = new LinkedValueConfiguration("TestValue");
            appCfg.start.parameters.add(pcfg);

            InstanceNodeConfiguration cfg = new InstanceNodeConfiguration();
            cfg.name = "Test";
            cfg.id = id;
            cfg.applications.add(appCfg);

            Path cfgDir = tmp.resolve("config");
            Files.createDirectories(cfgDir);
            Files.write(cfgDir.resolve("config.json"), Arrays.asList("DummyConfig"));

            Manifest.Key ifk = ifmb.setInstanceNodeConfiguration(cfg)
                    .addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME,
                            ProductManifest.of(hive, prodKey).getConfigTemplateTreeId())
                    .setMinionName(Minion.DEFAULT_NAME).insert(hive);

            InstanceConfiguration ic = new InstanceConfiguration();
            ic.name = "Test";
            ic.product = ProductManifest.of(hive, prodKey).getKey();
            ic.id = id;

            new InstanceManifest.Builder().setInstanceConfiguration(ic).addInstanceNodeManifest(Minion.DEFAULT_NAME, ifk)
                    .insert(hive);
        }

        {
            String imName = id + "/root";
            InstanceManifest im = InstanceManifest.of(hive, new Manifest.Key(imName,
                    hive.execute(new ManifestMaxIdOperation().setManifestName(imName)).get().toString()));

            assertEquals(1, im.getInstanceNodeManifestKeys().size());

            Path deploymentDir = tmp.resolve("fakeDeploy");

            Manifest.Key ifmRoot = im.getInstanceNodeManifestKeys().values().iterator().next();
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, ifmRoot);
            InstanceNodeController inmf = new InstanceNodeController(hive, new DeploymentPathProvider(tmp.resolve(deploymentDir), tmp.resolve("fakeLogData"),
                    inm), inm, new TaskSynchronizer());
            assertEquals("Test", inm.getConfiguration().name);
            assertEquals(1, inm.getConfiguration().applications.size());
            assertEquals("My Dummy", inm.getConfiguration().applications.iterator().next().name);
            assertEquals(appKey, inm.getConfiguration().applications.iterator().next().application);

            inmf.install();

            Path checkDir = deploymentDir.resolve(SpecialDirectory.MANIFEST_POOL.getDirName())
                    .resolve(appKey.directoryFriendlyName());

            // paths may change in the future, this is a little internal :)
            ContentHelper.checkDirsEqual(app, checkDir);

            // try to re-use the pooled application.
            inmf.uninstall();

            // right now, the pooled things stay alive - this might change in the future.
            ContentHelper.checkDirsEqual(app, checkDir);

            inmf.install();

            // right now, the pooled things stay alive - this might change in the future.
            ContentHelper.checkDirsEqual(app, checkDir);
        }
    }
}

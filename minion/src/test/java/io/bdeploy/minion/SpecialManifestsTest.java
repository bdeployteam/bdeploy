package io.bdeploy.minion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.ws.rs.core.UriBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class SpecialManifestsTest {

    @Test
    void customerManifest(BHive hive) {
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
    void productAndApplicationManifest(BHive hive, @TempDir Path tmp) throws IOException {
        Path app = TestAppFactory.createDummyApp("dummy", tmp);
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("app", OsHelper.getRunningOs()), "1.0");

        hive.execute(new ImportOperation().setSourcePath(app).setManifest(appKey));

        Manifest.Key prodKey = new Manifest.Key("prod", "1.0");
        ProductDescriptor pd = new ProductDescriptor();
        pd.name = "Dummy Product";
        pd.product = "prod";
        pd.applications.add("app");
        ProductManifest.Builder pmb = new ProductManifest.Builder(pd);
        pmb.add(appKey);
        pmb.insert(hive, prodKey, "Test Product");

        assertEquals("Test Product", ProductManifest.of(hive, prodKey).getProduct());

        ProductManifest pm = ProductManifest.of(hive, prodKey);
        assertNotNull(pm);
        assertEquals(1, pm.getApplications().size());
        assertEquals(appKey, pm.getApplications().first());

        ApplicationManifest am = ApplicationManifest.of(hive, appKey);
        assertEquals("dummy", am.getDescriptor().name);
    }

    @Test
    void instanceManifest(BHive hive, @TempDir Path tmp) throws IOException {
        String uuid = UuidHelper.randomId();
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("app", OsHelper.getRunningOs()), "1.0");
        Path app = TestAppFactory.createDummyApp("dummy", tmp);

        Manifest.Key jdkKey = new Manifest.Key(ScopedManifestKey.createScopedName("jdk", OsHelper.getRunningOs()), "1.8.0");
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        { // prepare test data
            hive.execute(new ImportOperation().setSourcePath(app).setManifest(appKey));
            hive.execute(new ImportOperation().setSourcePath(jdk).setManifest(jdkKey));
            ApplicationDescriptor desc = ApplicationManifest.of(hive, appKey).getDescriptor();

            Manifest.Key prodKey = new Manifest.Key("prod", "v1");
            ProductDescriptor pd = new ProductDescriptor();
            pd.name = "Dummy Product";
            pd.product = "prod";
            pd.applications.add("app");
            new ProductManifest.Builder(pd).add(appKey).insert(hive, prodKey, "Dummy Product");

            InstanceNodeManifest.Builder ifmb = new InstanceNodeManifest.Builder();

            ApplicationConfiguration appCfg = new ApplicationConfiguration();
            appCfg.application = appKey;
            appCfg.name = "My Dummy";
            appCfg.start = new CommandConfiguration();
            appCfg.start.executable = desc.startCommand.launcherPath;

            ParameterConfiguration pcfg = new ParameterConfiguration();
            pcfg.uid = "--param2";
            pcfg.value = "TestValue";
            appCfg.start.parameters.add(pcfg);

            InstanceNodeConfiguration cfg = new InstanceNodeConfiguration();
            cfg.name = "Test";
            cfg.uuid = uuid;
            cfg.applications.add(appCfg);

            Path cfgDir = tmp.resolve("config");
            Files.createDirectories(cfgDir);
            Files.write(cfgDir.resolve("config.json"), Arrays.asList("DummyConfig"));

            Manifest.Key ifk = ifmb.setInstanceNodeConfiguration(cfg).setConfigSource(cfgDir)
                    .setMinionName(Minion.DEFAULT_MASTER_NAME).insert(hive);

            InstanceConfiguration ic = new InstanceConfiguration();
            ic.name = "Test";
            ic.target = new RemoteService(UriBuilder.fromUri("https://dummy:123/api").build(), "DUMMY");
            ic.product = ProductManifest.of(hive, prodKey).getKey();
            ic.uuid = uuid;

            new InstanceManifest.Builder().setInstanceConfiguration(ic).addInstanceNodeManifest(Minion.DEFAULT_MASTER_NAME, ifk)
                    .insert(hive);
        }

        {
            String imName = uuid + "/root";
            InstanceManifest im = InstanceManifest.of(hive, new Manifest.Key(imName,
                    hive.execute(new ManifestMaxIdOperation().setManifestName(imName)).get().toString()));

            assertEquals(1, im.getInstanceNodeManifests().size());

            Manifest.Key ifmRoot = im.getInstanceNodeManifests().values().iterator().next();
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, ifmRoot);
            InstanceNodeController inmf = new InstanceNodeController(hive, tmp.resolve("d"), inm);
            assertEquals("Test", inm.getConfiguration().name);
            assertEquals(1, inm.getConfiguration().applications.size());
            assertEquals("My Dummy", inm.getConfiguration().applications.iterator().next().name);
            assertEquals(appKey, inm.getConfiguration().applications.iterator().next().application);

            inmf.install();

            // paths may change in the future, this is a little internal :)
            ContentHelper.checkDirsEqual(app,
                    tmp.resolve("d/" + uuid + "/bin/" + ifmRoot.getTag() + "/manifests/" + appKey.directoryFriendlyName()));
        }

    }

}

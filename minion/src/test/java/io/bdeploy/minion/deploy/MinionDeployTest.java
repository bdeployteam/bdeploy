package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.Minion;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class MinionDeployTest {

    @Test
    @SlowTest
    void testRemoteDeploy(BHive local, MasterRootResource master, RemoteService remote, @TempDir Path tmp)
            throws IOException, InterruptedException {
        /* STEP 1: Applications and external Application provided by development teams */
        Path app = TestAppFactory.createDummyApp("app", tmp);
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        Manifest.Key prodKey = new Manifest.Key("customer/product", "1.0.0.1234");
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("demo", OsHelper.getRunningOs()), "1.0.0.1234");
        Manifest.Key jdkKey = new Manifest.Key(ScopedManifestKey.createScopedName("jdk", OsHelper.getRunningOs()), "1.8.0");

        local.execute(new ImportOperation().setManifest(appKey).setSourcePath(app));
        local.execute(new ImportOperation().setManifest(jdkKey).setSourcePath(jdk));

        Path cfgs = tmp.resolve("config-templates");
        PathHelper.mkdirs(cfgs);
        Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

        ProductDescriptor pd = new ProductDescriptor();
        pd.name = "Dummy Product";
        pd.product = "customer";
        pd.applications.add("demo");
        pd.configTemplates = "config-templates";
        new ProductManifest.Builder(pd).add(appKey).setConfigTemplates(cfgs).insert(local, prodKey, "Demo Product for Unit Test");

        /* STEP 2: Create customer (normally via Web UI) and associated hive on remote */
        InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
        desc.name = "demo";
        desc.description = "For Unit Test";
        /* (note: usually this would be once locally (local HiveRegistry) as well as on the master). */
        /* (this test creates the named hive only on the target "remote" server - see below) */

        /* STEP 3a: Configuration created (normally via Web UI) */
        Manifest.Key instance = createDemoInstance(local, prodKey, tmp, remote);
        String uuid = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        /* STEP 3b: Establish sync with designated remote master */
        /* NOTE: alternative: sync via exported property file and master CLI in offline mode */
        master.addInstanceGroup(desc, master.getStorageLocations().iterator().next());
        // TODO: create CLI to do it via property file (e.g.) on master

        /* STEP 4: push instance manifest to remote master */
        /* NOTE: instance manifest references all other required things */
        local.execute(new PushOperation().setRemote(remote).setHiveName("demo").addManifest(instance));

        /* STEP 5: deploy, activate on remote master */
        assertFalse(master.getNamedMaster("demo").getAvailableDeployments().containsKey(uuid));
        assertFalse(master.getNamedMaster("demo").getAvailableDeployments(Minion.DEFAULT_MASTER_NAME).containsKey(uuid));
        master.getNamedMaster("demo").install(instance);
        assertTrue(master.getNamedMaster("demo").getAvailableDeployments().containsKey(uuid));
        assertTrue(master.getNamedMaster("demo").getAvailableDeployments(Minion.DEFAULT_MASTER_NAME).containsKey(uuid));
        master.getNamedMaster("demo").activate(instance);

        /* STEP 6: run/control processes on the remote */
        master.getNamedMaster("demo").start(uuid, "app");

        InstanceStatusDto status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertTrue(status.isAppRunningOrScheduled("app"));

        master.getNamedMaster("demo").stop(uuid, "app");
        status = master.getNamedMaster("demo").getStatus(uuid);
        System.out.println(status);
        assertFalse(status.isAppRunningOrScheduled("app"));
    }

    /**
     * Fakes the configuration UI.
     *
     * @param remote
     */
    private Manifest.Key createDemoInstance(BHive local, Manifest.Key product, Path tmp, RemoteService remote)
            throws IOException {
        String uuid = UuidHelper.randomId();

        /* STEP 1a: read available applications from product manifest */
        ProductManifest pmf = ProductManifest.of(local, product);

        /* STEP 1b: read application(s) to configure */
        ApplicationManifest amf = ApplicationManifest.of(local, pmf.getApplications().first());

        /* STEP 1c: create application configuration based on application descriptor */
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        cfg.uid = amf.getDescriptor().name;
        cfg.name = amf.getDescriptor().name;
        cfg.start = new CommandConfiguration();
        cfg.start.executable = amf.getDescriptor().startCommand.launcherPath;
        cfg.application = amf.getKey();
        cfg.processControl = ProcessControlConfiguration.createDefault();

        /* STEP 1d: configure parameters, usually in the UI based on information from the application descriptor */
        ParameterConfiguration sleepParam = new ParameterConfiguration();
        sleepParam.uid = "sleepParam";
        sleepParam.preRendered.add("10");
        cfg.start.parameters.add(sleepParam);

        /* STEP 1e: setup the node configuration, which basically only references all application configs */
        InstanceNodeConfiguration inc = new InstanceNodeConfiguration();
        inc.name = "Demo Instance";
        inc.uuid = uuid;
        inc.applications.add(cfg);

        /* STEP 2: create an node manifest per node which will participate (only master in this case */
        InstanceNodeManifest.Builder builder = new InstanceNodeManifest.Builder().setConfigTreeId(pmf.getConfigTemplateTreeId());
        Manifest.Key inmKey = builder.setInstanceNodeConfiguration(inc).setMinionName(Minion.DEFAULT_MASTER_NAME).insert(local);

        InstanceConfiguration ic = new InstanceConfiguration();
        ic.name = "Demo Instance";
        ic.product = pmf.getKey();
        ic.target = remote;
        ic.uuid = uuid;

        /* STEP 3: create an InstanceManifest with all instance node configurations. */
        // TODO: record the product manifest to enable updates late
        Manifest.Key imKey = new InstanceManifest.Builder().setInstanceConfiguration(ic)
                .addInstanceNodeManifest(Minion.DEFAULT_MASTER_NAME, inmKey).insert(local);

        return imKey; // this is the "root" - all instance artifacts are now reachable from here.
    }

}

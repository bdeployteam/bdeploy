package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupGroup;
import io.bdeploy.interfaces.cleanup.CleanupHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.launcher.cli.LauncherCli;
import io.bdeploy.launcher.cli.LauncherTool;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class MinionDeployTest {

    @RegisterExtension
    TestCliTool launcher = new TestCliTool(new LauncherCli());

    @Test
    @SlowTest
    void testRemoteDeploy(BHive local, MasterRootResource master, SlaveCleanupResource scr, RemoteService remote,
            @TempDir Path tmp, ActivityReporter reporter, MinionRoot mr) throws IOException, InterruptedException {
        /* STEP 1: Applications and external Application provided by development teams */
        Path app = TestAppFactory.createDummyApp("app", tmp);
        Path client = TestAppFactory.createDummyApp("client", tmp);
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        Manifest.Key prodKey = new Manifest.Key("customer/product", "1.0.0.1234");
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("demo", OsHelper.getRunningOs()), "1.0.0.1234");
        Manifest.Key clientKey = new Manifest.Key(ScopedManifestKey.createScopedName("demo-client", OsHelper.getRunningOs()),
                "1.0.0.1234");
        Manifest.Key jdkKey = new Manifest.Key(ScopedManifestKey.createScopedName("jdk", OsHelper.getRunningOs()), "1.8.0");

        local.execute(new ImportOperation().setManifest(appKey).setSourcePath(app));
        local.execute(new ImportOperation().setManifest(clientKey).setSourcePath(client));
        local.execute(new ImportOperation().setManifest(jdkKey).setSourcePath(jdk));

        Path cfgs = tmp.resolve("config-templates");
        PathHelper.mkdirs(cfgs);
        Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

        ProductDescriptor pd = new ProductDescriptor();
        pd.name = "Dummy Product";
        pd.product = "customer";
        pd.applications.add("demo");
        pd.configTemplates = "config-templates";
        new ProductManifest.Builder(pd).add(appKey).add(clientKey).setConfigTemplates(cfgs).insert(local, prodKey,
                "Demo Product for Unit Test");

        /* STEP 2: Create customer (normally via Web UI) and associated hive on remote */
        InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
        desc.name = "demo";
        desc.description = "For Unit Test";
        /* (note: usually this would be once locally (local HiveRegistry) as well as on the master). */
        /* (this test creates the named hive only on the target "remote" server - see below) */

        /* STEP 3a: Configuration created (normally via Web UI) */
        Manifest.Key instance = createDemoInstance(local, prodKey, tmp, remote, appKey, clientKey);
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

        /* STEP 7: generate client .bdeploy file and feed launcher */
        ClickAndStartDescriptor cdesc = new ClickAndStartDescriptor();
        cdesc.applicationId = "client";
        cdesc.groupId = "demo";
        cdesc.instanceId = uuid;
        cdesc.host = new RemoteService(remote.getUri(), master.getNamedMaster("demo").generateWeakToken("client"));

        Path bdeployFile = tmp.resolve("client.bdeploy");
        Files.write(bdeployFile, StorageHelper.toRawBytes(cdesc));

        /* STEP 8: launcher client, assert that the script does some sleeping... */
        launcher.getTool(LauncherTool.class, "--cacheDir=" + tmp.resolve("launcher"), "--launch=" + bdeployFile).run();

        // if we reach here, launching succeeded. unfortunately no better way to check right now.

        /* STEP 9: uninstall and cleanup - cleanup is a little whitebox, as usually the master cleanup job does this. */
        SortedSet<Manifest.Key> toKeep = new TreeSet<>();
        toKeep.add(Manifest.Key.parse(uuid + "/master:" + instance.getTag()));
        List<CleanupAction> nothingToDo = scr.cleanup(toKeep, false);
        assertTrue(nothingToDo.isEmpty());

        try (RemoteBHive rbh = RemoteBHive.forService(remote, "demo", reporter)) {
            rbh.removeManifest(instance); // remove top level instance.
        }

        // fake the registry containing all instance groups available on the master.
        BHiveRegistry fakeRegistry = new BHiveRegistry(reporter);
        mr.getStorageLocations().forEach(fakeRegistry::scanLocation);

        // same code as used by cleanup job and cleanup UI.
        SortedSet<Key> shouldBeEmpty = CleanupHelper.findAllUniqueKeys(fakeRegistry);
        List<CleanupGroup> groups = CleanupHelper.cleanAllMinions(mr.getMinions(), shouldBeEmpty, false);

        assertEquals(1, groups.size());
        assertEquals("master", groups.get(0).minion);

        // 1 instance node manifest, 1 application manifest, 1 dependent manifest
        // 1 instance version dir (not uninstalled before)
        // 1 instance data dir (last version removed), 2 stale pool dirs (application, dependent).
        assertEquals(7, groups.get(0).actions.size());

        // now actually do it.
        CleanupHelper.cleanAllMinions(groups, mr.getMinions());

        // no manifests should be left now
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> inventory = rbh.getManifestInventory();
            assertTrue(inventory.isEmpty());
        }

        // only the deployment dir itself and the pool directory is allowed to be alive, no other path may exist after cleanup.
        assertEquals(0,
                Files.walk(mr.getDeploymentDir())
                        .filter(p -> !p.equals(mr.getDeploymentDir())
                                && !p.equals(mr.getDeploymentDir().resolve(SpecialDirectory.MANIFEST_POOL.getDirName())))
                        .count());
    }

    /**
     * Fakes the configuration UI.
     *
     * @param remote
     */
    private Manifest.Key createDemoInstance(BHive local, Manifest.Key product, Path tmp, RemoteService remote,
            Manifest.Key serverApp, Manifest.Key clientApp) throws IOException {
        String uuid = UuidHelper.randomId();

        /* STEP 1a: read available applications from product manifest */
        ProductManifest pmf = ProductManifest.of(local, product);

        /* STEP 1b: read application(s) to configure */
        ApplicationManifest amf = ApplicationManifest.of(local, serverApp);
        ApplicationManifest camf = ApplicationManifest.of(local, clientApp);

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
        sleepParam.preRendered.add("3");
        cfg.start.parameters.add(sleepParam);

        /* STEP 1e: setup the node configuration, which basically only references all application configs */
        InstanceNodeConfiguration inc = new InstanceNodeConfiguration();
        inc.name = "DemoInstance";
        inc.uuid = uuid;
        inc.applications.add(cfg);

        /* STEP 1f: create application configuration based on application descriptor (client) */
        ApplicationConfiguration clientCfg = new ApplicationConfiguration();
        clientCfg.uid = camf.getDescriptor().name;
        clientCfg.name = camf.getDescriptor().name;
        clientCfg.start = new CommandConfiguration();
        clientCfg.start.executable = camf.getDescriptor().startCommand.launcherPath;
        clientCfg.application = camf.getKey();
        clientCfg.processControl = ProcessControlConfiguration.createDefault();

        /* STEP 1g: reuse parameters for client */
        clientCfg.start.parameters.add(sleepParam);

        /* STEP 1h: node config for the clients */
        InstanceNodeConfiguration cinc = new InstanceNodeConfiguration();
        cinc.name = "DemoInstance";
        cinc.uuid = uuid;
        cinc.applications.add(clientCfg);

        /* STEP 2: create an node manifest per node which will participate (master & clients) */
        InstanceNodeManifest.Builder builder = new InstanceNodeManifest.Builder().setConfigTreeId(pmf.getConfigTemplateTreeId());
        Manifest.Key inmKey = builder.setInstanceNodeConfiguration(inc).setMinionName(Minion.DEFAULT_MASTER_NAME).insert(local);

        // minion name does not "technically" matter here, real code uses '__ClientApplications'
        InstanceNodeManifest.Builder clientBuilder = new InstanceNodeManifest.Builder();
        Manifest.Key cinmKey = clientBuilder.setInstanceNodeConfiguration(cinc)
                .setMinionName(InstanceNodeConfigurationListDto.CLIENT_NODE_NAME).insert(local);

        InstanceConfiguration ic = new InstanceConfiguration();
        ic.name = "DemoInstance";
        ic.product = pmf.getKey();
        ic.target = remote;
        ic.uuid = uuid;

        /* STEP 3: create an InstanceManifest with all instance node configurations. */
        // TODO: record the product manifest to enable updates late
        Manifest.Key imKey = new InstanceManifest.Builder().setInstanceConfiguration(ic)
                .addInstanceNodeManifest(Minion.DEFAULT_MASTER_NAME, inmKey)
                .addInstanceNodeManifest(InstanceNodeConfigurationListDto.CLIENT_NODE_NAME, cinmKey).insert(local);

        return imKey; // this is the "root" - all instance artifacts are now reachable from here.
    }

}

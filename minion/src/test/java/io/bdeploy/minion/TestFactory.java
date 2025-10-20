package io.bdeploy.minion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration.ParameterConfigurationTarget;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.nodes.NodeType;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.pcu.TestAppFactory;
import io.bdeploy.ui.api.Minion;

/**
 * Factory class to create a small sample instance group for JUNIT tests.
 */
public class TestFactory {

    public static Manifest.Key createApplicationsAndInstance(BHive local, CommonRootResource root, RemoteService remote, Path tmp,
            boolean push) throws IOException {
        return createApplicationsAndInstance(local, root, remote, tmp, push, 0);
    }

    public static Manifest.Key createApplicationsAndInstance(BHive local, CommonRootResource root, RemoteService remote, Path tmp,
            boolean push, int port) throws IOException {
        return createApplicationsAndInstance(local, root, remote, tmp, Minion.DEFAULT_NAME, NodeType.SERVER, push, port);
    }

    public static Manifest.Key createApplicationsAndInstanceOnMultiNode(BHive local, CommonRootResource root,
            RemoteService remote, Path tmp, String nodeName) throws IOException {
        return createApplicationsAndInstance(local, root, remote, tmp, nodeName, NodeType.MULTI, true, 0);
    }

    private static Manifest.Key createApplicationsAndInstance(BHive local, CommonRootResource root, RemoteService remote,
            Path tmp, String targetNodeName, NodeType targetNodeType, boolean push, int port) throws IOException {
        /* STEP 1: Applications and external Application provided by development teams */
        Path app = TestAppFactory.createDummyApp("app", tmp, false, port);
        Path client = TestAppFactory.createDummyApp("client", tmp, true, 0);
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        Manifest.Key prodKey = new Manifest.Key("customer" + ProductManifestBuilder.PRODUCT_KEY_SUFFIX, "1.0.0.1234");
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("demo", OsHelper.getRunningOs()), "1.0.0.1234");
        Manifest.Key clientKey = new Manifest.Key(ScopedManifestKey.createScopedName("demo-client", OsHelper.getRunningOs()),
                "1.0.0.1234");
        Manifest.Key jdkKey = new Manifest.Key(ScopedManifestKey.createScopedName("jdk", OsHelper.getRunningOs()), "1.8.0");

        try (Transaction t = local.getTransactions().begin()) {
            local.execute(new ImportOperation().setManifest(appKey).setSourcePath(app));
            local.execute(new ImportOperation().setManifest(clientKey).setSourcePath(client));
            local.execute(new ImportOperation().setManifest(jdkKey).setSourcePath(jdk));
        }

        Path cfgs = tmp.resolve("config-templates");
        PathHelper.mkdirs(cfgs);
        Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

        ProductDescriptor pd = new ProductDescriptor();
        pd.name = "Dummy Product";
        pd.product = "customer";
        pd.applications.add("demo");
        pd.configTemplates = "config-templates";
        new ProductManifestBuilder(pd).add(appKey).add(clientKey).setConfigTemplates(cfgs).insert(local, prodKey,
                "Demo Product for Unit Test");

        Manifest.Key instance;
        try (Transaction t = local.getTransactions().begin()) {
            instance = createDemoInstance(local, prodKey, appKey, clientKey, targetNodeName, targetNodeType);
        }

        if (push) {
            InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
            desc.name = "demo";
            desc.title = "title";
            desc.description = "For Unit Test";

            root.addInstanceGroup(desc, root.getStorageLocations().iterator().next());

            local.execute(new PushOperation().setRemote(remote).setHiveName("demo").addManifest(instance).addManifest(prodKey)
                    .addManifest(jdkKey));
        }

        return instance;
    }

    /**
     * Fakes the configuration UI.
     */
    private static Manifest.Key createDemoInstance(BHive local, Manifest.Key product, Manifest.Key serverApp,
            Manifest.Key clientApp, String targetNodeName, NodeType targetNodeType) throws IOException {
        String id = "aaa-bbb-ccc";

        /* STEP 1a: read available applications from product manifest */
        ProductManifest pmf = ProductManifest.of(local, product);

        /* STEP 1b: read application(s) to configure */
        ApplicationManifest amf = ApplicationManifest.of(local, serverApp, null);
        ApplicationManifest camf = ApplicationManifest.of(local, clientApp, null);

        /* STEP 1c: create application configuration based on application descriptor */
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        cfg.id = amf.getDescriptor().name;
        cfg.name = amf.getDescriptor().name;
        cfg.start = new CommandConfiguration();
        cfg.start.executable = amf.getDescriptor().startCommand.launcherPath;
        cfg.application = amf.getKey();

        ProcessControlConfiguration processControlCfg = new ProcessControlConfiguration();
        processControlCfg.startType = ApplicationStartType.MANUAL;
        processControlCfg.keepAlive = false;
        processControlCfg.noOfRetries = 3;
        processControlCfg.gracePeriod = Duration.ofSeconds(30).toMillis();
        processControlCfg.attachStdin = false;

        cfg.processControl = processControlCfg;
        cfg.endpoints.http.addAll(amf.getDescriptor().endpoints.http);

        /* STEP 1d: configure parameters, usually in the UI based on information from the application descriptor */
        ParameterConfiguration sleepParam = new ParameterConfiguration();
        sleepParam.id = "sleepParam";
        sleepParam.preRendered.add("10");
        cfg.start.parameters.add(sleepParam);

        ParameterConfiguration envParam = new ParameterConfiguration();
        envParam.target = ParameterConfigurationTarget.ENVIRONMENT;
        envParam.id = "XENV";
        envParam.value = new LinkedValueConfiguration("Value");
        envParam.preRendered.add("XENV");
        envParam.preRendered.add("Value");
        cfg.start.parameters.add(envParam);

        /* STEP 1e: setup the node configuration, which basically only references all application configs */
        InstanceNodeConfiguration inc = new InstanceNodeConfiguration();
        inc.name = "DemoInstance";
        inc.id = id;
        inc.nodeType = targetNodeType;
        inc.applications.add(cfg);

        /* STEP 1f: create application configuration based on application descriptor (client) */
        ApplicationConfiguration clientCfg = new ApplicationConfiguration();
        clientCfg.id = camf.getDescriptor().name;
        clientCfg.name = camf.getDescriptor().name;
        clientCfg.start = new CommandConfiguration();
        clientCfg.start.executable = camf.getDescriptor().startCommand.launcherPath;
        clientCfg.application = camf.getKey();
        clientCfg.processControl = processControlCfg;

        /* STEP 1g: reuse parameters for client */
        clientCfg.start.parameters.add(sleepParam);

        /* STEP 1h: node config for the clients */
        InstanceNodeConfiguration cinc = new InstanceNodeConfiguration();
        cinc.name = "DemoInstance";
        cinc.id = id;
        cinc.nodeType = NodeType.CLIENT;
        cinc.applications.add(clientCfg);

        /* STEP 2: create an node manifest per node which will participate (master & clients) */
        InstanceNodeManifest.Builder builder = new InstanceNodeManifest.Builder()
                .addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME, pmf.getConfigTemplateTreeId());
        Manifest.Key inmKey = builder.setInstanceNodeConfiguration(inc).setMinionName(targetNodeName).insert(local);

        InstanceNodeManifest.Builder clientBuilder = new InstanceNodeManifest.Builder();
        Manifest.Key cinmKey = clientBuilder.setInstanceNodeConfiguration(cinc).setMinionName("DummyClientNode").insert(local);

        InstanceConfiguration ic = new InstanceConfiguration();
        ic.name = "DemoInstance";
        ic.description = "Demo Instance";
        ic.purpose = InstancePurpose.TEST;
        ic.product = pmf.getKey();
        ic.id = id;
        ic.configTree = pmf.getConfigTemplateTreeId();

        /* STEP 3: create an InstanceManifest with all instance node configurations. */
        // This is the "root" - all instance artifacts are now reachable from here.
        return new InstanceManifest.Builder().setInstanceConfiguration(ic).addInstanceNodeManifest(targetNodeName, inmKey)
                .addInstanceNodeManifest("DummyClientNode", cinmKey).insert(local);
    }

    /**
     * Pushes a new product with a single application into the given remote hive.
     *
     * @param groupName
     *            the name of the instance group
     * @param remote
     *            the remote service
     * @param tmp
     *            a local temporary directory required to generate files
     * @return the manifest of the created product
     */
    public static ProductManifest pushProduct(String groupName, RemoteService remote, Path tmp) throws IOException {
        Manifest.Key prodKey = new Manifest.Key("prod", "1.2.3");
        Manifest.Key appKey = new Manifest.Key(ScopedManifestKey.createScopedName("app", OsHelper.getRunningOs()), "1.2.3");

        ApplicationDescriptor cfg = new ApplicationDescriptor();
        cfg.name = "MyApp";
        Path appPath = tmp.resolve("app");
        PathHelper.mkdirs(appPath);
        Files.write(appPath.resolve(ApplicationDescriptorApi.FILE_NAME),
                JacksonHelper.getDefaultYamlObjectMapper().writeValueAsBytes(cfg));

        try (BHive hive = new BHive(tmp.resolve("hive").toUri(), null, new ActivityReporter.Null())) {
            try (Transaction t = hive.getTransactions().begin()) {
                hive.execute(new ImportOperation().setManifest(appKey).setSourcePath(appPath));
            }

            Path cfgs = tmp.resolve("config-templates");
            PathHelper.mkdirs(cfgs);
            Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

            ProductDescriptor pd = new ProductDescriptor();
            pd.name = "Dummy Product";
            pd.product = "prod";
            pd.applications.add(appKey.getName());
            pd.configTemplates = "config-templates";
            new ProductManifestBuilder(pd).add(appKey).setConfigTemplates(cfgs).insert(hive, prodKey, "Dummy Product");
            hive.execute(new PushOperation().addManifest(prodKey).setHiveName(groupName).setRemote(remote));

            return ProductManifest.of(hive, prodKey);
        }
    }

    /**
     * Creates a new application configuration that references the first application in the given product.
     *
     * @param product
     *            the product defining the application
     * @return the application configuration
     */
    public static ApplicationConfiguration createAppConfig(ProductManifest product) {
        ApplicationConfiguration appCfg = new ApplicationConfiguration();
        appCfg.application = product.getApplications().first();
        appCfg.name = "MyApp";
        appCfg.start = new CommandConfiguration();
        appCfg.start.executable = "foo.cmd";
        return appCfg;
    }

    /**
     * Creates a new instance group configuration.
     *
     * @param name
     *            the name of the group
     * @return the created configuration
     */
    public static InstanceGroupConfiguration createInstanceGroup(String name) {
        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = name;
        group.description = name;
        return group;
    }

    /**
     * Creates a new instance
     *
     * @param name
     *            the name of the instance
     * @param product
     *            the product to use
     * @return the create configuration
     */
    public static InstanceConfiguration createInstanceConfig(String name, ProductManifest product) {
        InstanceConfiguration instanceConfig = new InstanceConfiguration();
        instanceConfig.product = product.getKey();
        instanceConfig.id = name;
        instanceConfig.name = name;
        return instanceConfig;
    }

    /**
     * Creates a new software repository configuration.
     *
     * @param name
     *            the name of the software repository
     * @return the created configuration
     */
    public static SoftwareRepositoryConfiguration createSoftwareRepository(String name) {
        SoftwareRepositoryConfiguration repo = new SoftwareRepositoryConfiguration();
        repo.name = name;
        repo.description = "description of " + name;
        return repo;
    }

    /**
     * Filter a given set of manifest keys for some volatile manifest which can change due to background jobs etc. - they would
     * influence tests in a bad way.
     */
    public static SortedMap<Key, ObjectId> getFilteredManifests(SortedMap<Key, ObjectId> sortedMap) {
        var result = new TreeMap<Key, ObjectId>();
        for (var entry : sortedMap.entrySet()) {
            if (MinionManifest.MANIFEST_NAME.equals(entry.getKey().getName())) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}

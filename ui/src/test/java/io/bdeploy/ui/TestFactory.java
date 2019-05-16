package io.bdeploy.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

/**
 * Factory class to create products, applications to use in a test.
 */
public class TestFactory {

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
        Files.write(appPath.resolve(ApplicationDescriptor.FILE_NAME),
                JacksonHelper.createObjectMapper(MapperType.YAML).writeValueAsBytes(cfg));

        try (BHive hive = new BHive(tmp.resolve("hive").toUri(), new ActivityReporter.Null())) {
            hive.execute(new ImportOperation().setManifest(appKey).setSourcePath(appPath));

            Path cfgs = tmp.resolve("config-templates");
            PathHelper.mkdirs(cfgs);
            Files.write(cfgs.resolve("myconfig.json"), Arrays.asList("{ \"cfg\": \"value\" }"));

            ProductDescriptor pd = new ProductDescriptor();
            pd.name = "Dummy Product";
            pd.product = "prod";
            pd.applications.add(appKey.getName());
            pd.configTemplates = "config-templates";
            new ProductManifest.Builder(pd).add(appKey).setConfigTemplates(cfgs).insert(hive, prodKey, "Dummy Product");
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
     * @param remote
     *            the master URL
     * @param product
     *            the product to use
     * @return the create configuration
     */
    public static InstanceConfiguration createInstanceConfig(String name, RemoteService remote, ProductManifest product) {
        InstanceConfiguration instanceConfig = new InstanceConfiguration();
        instanceConfig.product = product.getKey();
        instanceConfig.uuid = name;
        instanceConfig.name = name;
        instanceConfig.target = remote;
        return instanceConfig;
    }

}

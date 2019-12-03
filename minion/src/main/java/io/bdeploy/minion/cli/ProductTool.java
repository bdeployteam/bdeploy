package io.bdeploy.minion.cli;

import java.nio.file.Paths;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.cfg.Configuration.ConfigurationNameMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.dependencies.DependencyFetcher;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;
import io.bdeploy.interfaces.manifest.dependencies.RemoteDependencyFetcher;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.ProductTool.ProductConfig;

/**
 * Manages product manifests.
 */
@Help("Manage product manifests.")
@CliName("product")
public class ProductTool extends RemoteServiceTool<ProductConfig> {

    public @interface ProductConfig {

        @Help("The hive to operate on.")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help("Creates a product from a product descriptor. Either a path to a YAML file, or a path to a directory containing a 'product-info.yaml' file.")
        @ConfigurationNameMapping("import")
        @Validator(ExistingPathValidator.class)
        String imp();

        @Help("Name of the target instance group to import the product into")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "When given, push the result in the specified instance group on the given remote", arg = false)
        boolean push() default false;

        @Help(value = "When given, list all product manifests.", arg = false)
        boolean list() default false;
    }

    public ProductTool() {
        super(ProductConfig.class);
    }

    @Override
    protected void run(ProductConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            if (config.list()) {
                listProducts(hive);
            } else if (config.imp() != null) {
                importProduct(config, svc, hive);
            } else {
                helpAndFail("Missing --list or --import argument");
            }
        }

    }

    private void importProduct(ProductConfig config, RemoteService svc, BHive hive) {
        DependencyFetcher fetcher;
        if (svc != null) {
            fetcher = new RemoteDependencyFetcher(svc, config.instanceGroup(), getActivityReporter());
        } else {
            fetcher = new LocalDependencyFetcher();
        }

        Manifest.Key key = ProductManifest.importFromDescriptor(Paths.get(config.imp()), hive, fetcher, true);

        if (config.push()) {
            helpAndFailIfMissing(svc, "missing --remote");

            TransferStatistics stats = hive
                    .execute(new PushOperation().setRemote(svc).setHiveName(config.instanceGroup()).addManifest(key));

            out().println(String.format("Pushed %1$d manifests. %2$d of %3$d trees reused, %4$d objects sent (%5$s)",
                    stats.sumManifests, stats.sumTrees - stats.sumMissingTrees, stats.sumTrees, stats.sumMissingObjects,
                    UnitHelper.formatFileSize(stats.transferSize)));
        }
    }

    private void listProducts(BHive hive) {
        SortedSet<Key> scan = ProductManifest.scan(hive);
        for (Key key : scan) {
            out().println(String.format("%1$-50s %2$-30s", key, ProductManifest.of(hive, key).getProduct()));

            if (isVerbose()) {
                ProductManifest pmf = ProductManifest.of(hive, key);
                for (Manifest.Key appKey : pmf.getApplications()) {
                    ApplicationManifest amf = ApplicationManifest.of(hive, appKey);
                    out().println(String.format("  %1$-48s %2$-30s", appKey, amf.getDescriptor().name));
                }
                out().println("  Other References:");
                for (Manifest.Key otherKey : pmf.getReferences()) {
                    out().println(String.format("    %1$-48s", otherKey));
                }
            }
        }
    }

}

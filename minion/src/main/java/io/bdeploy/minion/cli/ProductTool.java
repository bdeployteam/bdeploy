package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedSet;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.RemoteDependencyFetcher;
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
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.logging.audit.RollingFileAuditor;
import io.bdeploy.minion.cli.ProductTool.ProductConfig;

/**
 * Manages product manifests.
 */
@Help("Create and manage product manifests locally")
@ToolCategory(MinionServerCli.PRODUCT_TOOLS)
@CliName("product")
public class ProductTool extends RemoteServiceTool<ProductConfig> {

    public @interface ProductConfig {

        @Help("The hive to operate on.")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
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
    protected RenderableResult run(ProductConfig config, @RemoteOptional RemoteService svc) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        Path hivePath = Paths.get(config.hive());
        try (BHive hive = new BHive(hivePath.toUri(), RollingFileAuditor.getFactory().apply(hivePath), getActivityReporter())) {
            if (config.list()) {
                return listProducts(hive);
            } else if (config.imp() != null) {
                return importProduct(config, svc, hive);
            } else {
                return createNoOp();
            }
        }

    }

    private DataResult importProduct(ProductConfig config, RemoteService svc, BHive hive) {
        DependencyFetcher fetcher;
        if (svc != null) {
            fetcher = new RemoteDependencyFetcher(svc, config.instanceGroup(), getActivityReporter());
        } else {
            fetcher = new LocalDependencyFetcher();
        }

        DataResult result = createSuccess();
        Manifest.Key key = ProductManifestBuilder.importFromDescriptor(Paths.get(config.imp()), hive, fetcher, true);

        result.addField("Product Manifest", key);

        if (config.push()) {
            helpAndFailIfMissing(svc, "missing --remote");

            TransferStatistics stats = hive
                    .execute(new PushOperation().setRemote(svc).setHiveName(config.instanceGroup()).addManifest(key));
            stats.toResult(result);
        }
        return result;
    }

    private DataTable listProducts(BHive hive) {
        SortedSet<Key> scan = ProductManifest.scan(hive);
        DataTable table = createDataTable();
        table.setCaption("Products in " + hive.getUri());

        table.column("Name", 25).column("Key", 30).column("Version", 20);
        table.column(new DataTableColumn("NoOfApplications", "# App.", 6));
        table.column(new DataTableColumn("NoOfReferences", "# Ref.", 6));
        table.column(new DataTableColumn("NoOfPlugins", "# Plug.", 7));
        table.column(new DataTableColumn("NoOfInstanceTemplates", "# Ins.Templ.", 12));
        table.column(new DataTableColumn("NoOfApplicationTemplates", "# App.Templ.", 12));

        for (Key key : scan) {
            ProductManifest pmf = ProductManifest.of(hive, key);

            table.row().cell(pmf.getProductDescriptor().name).cell(key.getName()).cell(key.getTag())
                    .cell(pmf.getApplications().size()).cell(pmf.getReferences().size()).cell(pmf.getPlugins().size())
                    .cell(pmf.getInstanceTemplates().size()).cell(pmf.getApplicationTemplates().size()).build();
        }
        return table;
    }

}

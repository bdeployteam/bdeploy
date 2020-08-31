package io.bdeploy.ui.cli;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.cli.RemoteProductTool.ProductConfig;
import io.bdeploy.ui.dto.ProductDto;

@Help("List products on remote server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-product")
public class RemoteProductTool extends RemoteServiceTool<ProductConfig> {

    public @interface ProductConfig {

        @Help("Name of the instance group for import into or export from")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "List instance versions on the remote", arg = false)
        boolean list() default false;

    }

    public RemoteProductTool() {
        super(ProductConfig.class);
    }

    @Override
    protected RenderableResult run(ProductConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        if (config.list()) {
            return list(remote, config);
        } else {
            return createNoOp();
        }
    }

    private DataTable list(RemoteService remote, ProductConfig config) {
        DataTable table = createDataTable();
        table.setCaption("Products in " + config.instanceGroup() + " on " + remote.getUri());

        table.column("Name", 25).column("Key", 30).column("Version", 20);
        table.column(new DataTableColumn("NoOfInstanceTemplates", "# Ins.Templ.", 12));
        table.column(new DataTableColumn("NoOfApplicationTemplates", "# App.Templ.", 12));

        ProductResource pr = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getProductResource(config.instanceGroup());

        for (ProductDto dto : pr.list(null)) {
            table.row().cell(dto.name).cell(dto.key.getName()).cell(dto.key.getTag()).cell(dto.instanceTemplates.size())
                    .cell(dto.applicationTemplates.size()).build();
        }

        return table;
    }

}

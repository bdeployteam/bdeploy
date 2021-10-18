package io.bdeploy.ui.cli;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.cli.RemoteProductTool.ProductConfig;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;

@Help("List products on remote server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-product")
public class RemoteProductTool extends RemoteServiceTool<ProductConfig> {

    public @interface ProductConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "List products on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Copy a product from a software repository", arg = false)
        boolean copy() default false;

        @Help(value = "Transfer product from central to given managed server")
        String transferToManaged();

        @Help(value = "The source software repository for --copy")
        String repository();

        @Help(value = "The product version to copy")
        String product();

    }

    public RemoteProductTool() {
        super(ProductConfig.class);
    }

    @Override
    protected RenderableResult run(ProductConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup missing");

        if (config.list()) {
            return list(remote, config);
        } else if (config.copy()) {
            helpAndFailIfMissing(config.repository(), "Missing --repository");
            helpAndFailIfMissing(config.product(), "Missing --product");
            return copy(remote, config);
        } else if (config.transferToManaged() != null) {
            helpAndFailIfMissing(config.product(), "Missing --product");
            return transferToManaged(remote, config);
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

    private DataResult copy(RemoteService remote, ProductConfig config) {

        ProductResource pr = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getProductResource(config.instanceGroup());
        Manifest.Key pkey = Manifest.Key.parse(config.product());
        pr.copyProduct(config.repository(), pkey.getName() + "/product", pkey.getTag());

        return createSuccess();
    }

    private DataResult transferToManaged(RemoteService remote, ProductConfig config) {
        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode != MinionMode.CENTRAL) {
            return createResultWithMessage("Action only available on CENTRAL server.");
        }

        ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());
        Manifest.Key pkey = Manifest.Key.parse(config.product());

        ProductResource pr = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getProductResource(config.instanceGroup());

        List<ProductDto> products = pr.list(null);
        Optional<ProductDto> dto = products.stream()
                .filter(p -> Objects.equals(p.key.getName(), pkey.getName()) && Objects.equals(p.key.getTag(), pkey.getTag()))
                .findFirst();

        if (dto.isEmpty()) {
            return createResultWithMessage("Product not found on central server");
        }

        ProductTransferDto ptd = new ProductTransferDto();
        ptd.sourceMode = MinionMode.CENTRAL;
        ptd.targetMode = MinionMode.MANAGED;
        ptd.targetServer = config.transferToManaged();
        ptd.versionsToTransfer = Collections.singletonList(dto.get());

        msr.transferProducts(config.instanceGroup(), ptd);

        while (msr.getActiveTransfers(config.instanceGroup()).stream().filter(d -> d.compareTo(dto.get()) == 0).findAny()
                .isPresent()) {
            out().println("Waiting for transfer.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        return createSuccess();
    }

}

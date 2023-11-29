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
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.cli.RemoteProductTool.ProductConfig;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;

@Help("List products on remote server")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-product")
public class RemoteProductTool extends RemoteServiceTool<ProductConfig> {

    public @interface ProductConfig {

        @Help("Name of the instance group.")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "List products on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Copy a product from a software repository", arg = false)
        boolean copy() default false;

        @Help(value = "Transfer product from central to given managed server")
        String transferToManaged();

        @Help(value = "The source software repository.")
        String repository();

        @Help(value = "The product version to copy")
        String product();

        @Help(value = "A product version to show details about.")
        String details();

    }

    public RemoteProductTool() {
        super(ProductConfig.class);
    }

    @Override
    protected RenderableResult run(ProductConfig config, RemoteService remote) {
        if (config.repository() == null && config.instanceGroup() == null) {
            helpAndFail("--instanceGroup or --repository missing");
        }

        if (config.list()) {
            return list(remote, config);
        } else if (config.copy()) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.repository(), "Missing --repository");
            helpAndFailIfMissing(config.product(), "Missing --product");
            return copy(remote, config);
        } else if (config.transferToManaged() != null) {
            helpAndFailIfMissing(config.instanceGroup(), "Missing --instanceGroup");
            helpAndFailIfMissing(config.product(), "Missing --product");
            return transferToManaged(remote, config);
        } else if (config.details() != null) {
            return showDetails(remote, config);
        } else {
            return createNoOp();
        }
    }

    private ProductResource getProductRsrc(RemoteService svc, ProductConfig config) {
        if (config.instanceGroup() != null) {
            return ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext())
                    .getProductResource(config.instanceGroup());
        } else if (config.repository() != null) {
            return ResourceProvider.getResource(svc, SoftwareRepositoryResource.class, getLocalContext())
                    .getProductResource(config.repository());
        }

        throw new IllegalStateException("Need either --instanceGroup or --repository");
    }

    private RenderableResult showDetails(RemoteService remote, ProductConfig config) {
        DataResult result = createEmptyResult();

        ProductResource pr = getProductRsrc(remote, config);

        Manifest.Key key = Manifest.Key.parse(config.details());

        Optional<ProductDto> dto = pr.list(key.getName()).stream().filter(p -> p.key.getTag().equals(key.getTag())).findFirst();

        if (dto.isEmpty()) {
            throw new IllegalStateException("Cannot find product: " + config.details());
        }

        ProductDto prod = dto.get();
        result.addField("Key", prod.key.toString());
        result.addField("ID", prod.product);
        result.addField("Name", prod.name);
        result.addField("Vendor", prod.vendor);
        result.addField("Config. Tree ID", prod.configTree);
        result.addField("Labels",
                String.join("\n", prod.labels.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList()));
        result.addField("Application Templates", String.join("\n", prod.applicationTemplates.stream().map(t -> t.name).toList()));
        result.addField("Instance Templates", String.join("\n", prod.instanceTemplates.stream().map(t -> t.name).toList()));
        result.addField("Dependencies",
                String.join("\n", prod.references.stream().map(r -> r.getName() + ":" + r.getTag()).toList()));

        // applications?
        // plugins?

        return result;
    }

    private DataTable list(RemoteService remote, ProductConfig config) {
        DataTable table = createDataTable();
        table.setCaption("Products in " + config.instanceGroup() != null ? config.instanceGroup()
                : config.repository() + " on " + remote.getUri());

        table.column("Name", 25).column("Key", 30).column("Version", 20);
        table.column(new DataTableColumn("NoOfInstanceTemplates", "# Ins.Templ.", 12));
        table.column(new DataTableColumn("NoOfApplicationTemplates", "# App.Templ.", 12));

        ProductResource pr = getProductRsrc(remote, config);
        ;

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
        pr.copyProduct(config.repository(), pkey.getName() + "/product", List.of(pkey.getTag()));

        return createSuccess();
    }

    private DataResult transferToManaged(RemoteService remote, ProductConfig config) {
        BackendInfoResource bir = ResourceProvider.getResource(remote, BackendInfoResource.class, getLocalContext());
        if (bir.getVersion().mode != MinionMode.CENTRAL) {
            return createResultWithErrorMessage("Action only available on CENTRAL server.");
        }

        ManagedServersResource msr = ResourceProvider.getResource(remote, ManagedServersResource.class, getLocalContext());
        Manifest.Key pkey = Manifest.Key.parse(config.product());

        ManagedMasterDto msrv = null;
        for (var srv : msr.getManagedServers(config.instanceGroup())) {
            if (srv.hostName.equals(config.transferToManaged())) {
                msrv = srv;
            }
        }
        if (msrv == null) {
            return createResultWithErrorMessage(
                    "Managed Server " + config.transferToManaged() + " does not exist in " + config.instanceGroup());
        }

        ProductResource pr = ResourceProvider.getResource(remote, InstanceGroupResource.class, getLocalContext())
                .getProductResource(config.instanceGroup());

        List<ProductDto> products = pr.list(null);
        Optional<ProductDto> dto = products.stream()
                .filter(p -> Objects.equals(p.key.getName(), pkey.getName()) && Objects.equals(p.key.getTag(), pkey.getTag()))
                .findFirst();

        if (dto.isEmpty()) {
            return createResultWithErrorMessage("Product not found on central server");
        }

        ProductTransferDto ptd = new ProductTransferDto();
        ptd.sourceMode = MinionMode.CENTRAL;
        ptd.targetMode = MinionMode.MANAGED;
        ptd.targetServer = config.transferToManaged();
        ptd.versionsToTransfer = Collections.singletonList(dto.get());

        msr.transferProducts(config.instanceGroup(), ptd);

        while (msr.getActiveTransfers(config.instanceGroup()).stream().anyMatch(d -> d.compareTo(dto.get()) == 0)) {
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

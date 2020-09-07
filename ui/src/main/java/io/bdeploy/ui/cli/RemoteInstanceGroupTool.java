package io.bdeploy.ui.cli;

import java.util.List;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool.RemoteInstanceGroupConfig;
import io.bdeploy.ui.dto.InstanceDto;

@Help("Create instance group/hive on the remote")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-group")
public class RemoteInstanceGroupTool extends RemoteServiceTool<RemoteInstanceGroupConfig> {

    public @interface RemoteInstanceGroupConfig {

        @Help("Instance Group (and named BHive) to create. Short file-system suitable name.")
        String create();

        @Help("Instance Group display name to be set on creation.")
        String title();

        @Help("Description of the customer")
        String description();

        @Help("Delete the given instance group (and associated BHive). This CANNOT BE UNDONE.")
        String delete();

        @Help(value = "List existing instance groups on the remote", arg = false)
        boolean list() default false;
    }

    public RemoteInstanceGroupTool() {
        super(RemoteInstanceGroupConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteInstanceGroupConfig config, RemoteService svc) {
        InstanceGroupResource client = ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext());

        if (config.create() != null) {
            helpAndFailIfMissing(config.description(), "Missing description");

            InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
            desc.name = config.create();
            desc.description = config.description();
            desc.title = config.title();

            client.create(desc);
            return createSuccess();
        } else if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("Instance Groups on " + svc.getUri());
            table.column("Name", 20).column("Title", 20).column(new DataTableColumn("instanceCount", "# Ins.", 6))
                    .column("Description", 50);

            for (InstanceGroupConfiguration cfg : client.list()) {
                List<InstanceDto> ics = client.getInstanceResource(cfg.name).list();
                table.row().cell(cfg.name).cell(cfg.title).cell(ics.size()).cell(cfg.description).build();
            }
            return table;
        } else if (config.delete() != null) {
            // don't use out() here, really make sure the warning appears on screen.
            String confirmation = System.console().readLine(
                    "Delete %1$s? This CANNOT be undone. Type the name of the Instance Group to delete to confirm: ",
                    config.delete());

            if (confirmation != null && confirmation.equals(config.delete())) {
                client.delete(config.delete());
                return createSuccess();
            } else {
                return createResultWithMessage("Aborted, no confirmation");
            }
        } else {
            return createNoOp();
        }

    }

}

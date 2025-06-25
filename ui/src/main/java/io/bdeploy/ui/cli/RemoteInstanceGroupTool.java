package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.FormDataHelper;
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

        @Help("Description of the instance group")
        String description();

        @Help("Path to an icon file.")
        @Validator(ExistingPathValidator.class)
        String icon();

        @Help("Delete the given instance group (and associated BHive). This CANNOT BE UNDONE.")
        String delete();

        @Help(value = "List existing instance groups on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Automatically cleanup old/unused product versions")
        boolean autoCleanup() default true;

        @Help("Invalidate any cached information related to storage in the given instance group")
        String invalidateCachesOn();
    }

    public RemoteInstanceGroupTool() {
        super(RemoteInstanceGroupConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteInstanceGroupConfig config, RemoteService svc) {
        InstanceGroupResource client = ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext());

        if (config.create() != null) {
            Path iconPath = null;
            if (config.icon() != null && !config.icon().isBlank()) {
                iconPath = Paths.get(config.icon());
                if (!Files.isRegularFile(iconPath)) {
                    helpAndFail("--icon is not a regular file");
                }
            }

            InstanceGroupConfiguration desc = new InstanceGroupConfiguration();
            desc.name = config.create();
            desc.description = config.description();
            desc.title = config.title();
            desc.autoDelete = config.autoCleanup();

            if (desc.title == null) {
                // fallback - make sure a title is set.
                desc.title = desc.name;
            }

            client.create(desc);

            if (iconPath != null) {
                try (InputStream is = Files.newInputStream(iconPath);
                        FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("image", is)) {
                    client.updateImage(config.create(), fdmp);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot upload image", e);
                }
            }
            return createSuccess();
        } else if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("Instance Groups on " + svc.getUri());
            table.column(new DataTableColumn.Builder("Name").setMinWidth(13).build());
            table.column(new DataTableColumn.Builder("Title").setMinWidth(13).build());
            table.column(new DataTableColumn.Builder("# Ins.").setName("instanceCount").setMinWidth(6).build());
            table.column(new DataTableColumn.Builder("Description").setMinWidth(0).build());

            List<InstanceGroupConfiguration> cfgList = client.list().stream().map(cfg -> cfg.instanceGroupConfiguration).toList();
            for (InstanceGroupConfiguration cfg : cfgList) {
                List<InstanceDto> ics = client.getInstanceResource(cfg.name).list();
                table.row().cell(cfg.name).cell(cfg.title).cell(ics.size()).cell(cfg.description).build();
            }
            return table;
        } else if(config.invalidateCachesOn() != null) {
            client.invalidateCaches(config.invalidateCachesOn());
            return createSuccess();
        } else if (config.delete() != null) {
            // don't use out() here, really make sure the warning appears on screen.
            String confirmation = System.console()
                    .readLine("Delete %1$s? This CANNOT be undone. Type the name of the Instance Group to delete to confirm: ",
                            config.delete());

            if (confirmation != null && confirmation.equals(config.delete())) {
                client.delete(config.delete());
                return createSuccess();
            } else {
                return createResultWithErrorMessage("Aborted, no confirmation");
            }
        } else {
            return createNoOp();
        }
    }

}

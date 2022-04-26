package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

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
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.cli.RemoteInstanceGroupTool.RemoteInstanceGroupConfig;
import io.bdeploy.ui.dto.InstanceDto;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

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

        @Help("Path to an icon file.")
        @Validator(ExistingPathValidator.class)
        String icon();

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

            if (desc.title == null) {
                // fallback - make sure a title is set.
                desc.title = desc.name;
            }

            client.create(desc);

            if (iconPath != null) {
                try (InputStream is = Files.newInputStream(iconPath)) {
                    try (MultiPart mp = new MultiPart()) {
                        StreamDataBodyPart bp = new StreamDataBodyPart("image", is);
                        bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                        mp.bodyPart(bp);

                        WebTarget target = JerseyClientFactory.get(svc)
                                .getBaseTarget(new JerseyOnBehalfOfFilter(getLocalContext()))
                                .path("/group/" + config.create() + "/image");
                        Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                            throw new IllegalStateException("Image upload failed: " + response.getStatusInfo().getStatusCode()
                                    + ": " + response.getStatusInfo().getReasonPhrase());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot upload image", e);
                }
            }
            return createSuccess();
        } else if (config.list()) {
            DataTable table = createDataTable();
            table.setCaption("Instance Groups on " + svc.getUri());
            table.column("Name", 20).column("Title", 20).column(new DataTableColumn("instanceCount", "# Ins.", 6))
                    .column("Description", 50);

            List<InstanceGroupConfiguration> cfgList = client.list().stream().map(cfg -> cfg.instanceGroupConfiguration)
                    .collect(Collectors.toList());
            for (InstanceGroupConfiguration cfg : cfgList) {
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

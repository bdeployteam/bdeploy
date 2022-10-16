package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.PluginResource;
import io.bdeploy.ui.cli.RemotePluginTool.RemotePluginConfig;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

@Help("Investigate a remote master's plugins")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-plugin")
public class RemotePluginTool extends RemoteServiceTool<RemotePluginConfig> {

    public @interface RemotePluginConfig {

        @Help(value = "List plugins", arg = false)
        boolean list() default false;

        @Help("Path to a plugin JAR file which should be added as global plugin")
        String add();

        @Help(value = "Replace an existing plugin of the same name and version", arg = false)
        boolean replace() default false;

        @Help("ID of a global plugin to unload and uninstall from the server")
        String remove();

        @Help("ID of a plugin to unload. Global plugins will be reloaded on next server start")
        String unload();

        @Help("ID of a currently unloaded global plugin to load")
        String load();

    }

    public RemotePluginTool() {
        super(RemotePluginConfig.class);
    }

    @Override
    protected RenderableResult run(RemotePluginConfig config, RemoteService svc) {
        PluginResource client = ResourceProvider.getResource(svc, PluginResource.class, getLocalContext());

        if (config.list()) {
            List<PluginInfoDto> loaded = client.getLoadedPlugins();
            List<PluginInfoDto> notLoaded = client.getNotLoadedGlobalPlugin();

            DataTable table = createDataTable();
            table.setCaption("Plugins loaded on " + svc.getUri());
            table.column("ID", 40).column("Name", 30).column("Version", 10).column("Loaded", 6).column("Global", 6);

            for (PluginInfoDto dto : loaded) {
                table.row().cell(dto.id).cell(dto.name).cell(dto.version).cell("*").cell(dto.global ? "*" : "").build();
            }

            for (PluginInfoDto dto : notLoaded) {
                table.row().cell(dto.id).cell(dto.name).cell(dto.version).cell("").cell(dto.global ? "*" : "").build();
            }
            return table;
        } else if (config.add() != null) {
            try {
                return addPlugin(svc, config.add(), config.replace());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot add plugin", e);
            }
        } else if (config.unload() != null) {
            client.unloadPlugin(ObjectId.parse(config.unload()));
        } else if (config.load() != null) {
            client.loadGlobalPlugin(ObjectId.parse(config.load()));
        } else if (config.remove() != null) {
            client.deleteGlobalPlugin(ObjectId.parse(config.remove()));
        } else {
            return createNoOp();
        }
        return createSuccess();
    }

    private DataResult addPlugin(RemoteService svc, String file, boolean replace) throws IOException {
        Path plugin = Paths.get(file);
        if (!Files.isRegularFile(plugin)) {
            return createResultWithErrorMessage("Not a file: " + file);
        }

        try (InputStream is = Files.newInputStream(plugin)) {
            try (MultiPart mp = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("plugin", is);
                bp.setFilename("plugin.jar");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                mp.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget(new JerseyOnBehalfOfFilter(getLocalContext()))
                        .path("/plugin-admin/upload-global");
                if (replace) {
                    target = target.queryParam("replace", true);
                }
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new IllegalStateException("Upload failed: " + response.getStatusInfo().getStatusCode() + ": "
                            + response.getStatusInfo().getReasonPhrase());
                }

                PluginInfoDto pid = response.readEntity(PluginInfoDto.class);

                return createSuccess().addField("Plugin Name", pid.name).addField("Plugin Version", pid.version)
                        .addField("Plugin ID", pid.id);
            }
        }
    }

}

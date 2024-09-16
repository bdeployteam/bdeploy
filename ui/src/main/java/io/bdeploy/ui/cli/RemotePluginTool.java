package io.bdeploy.ui.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.PluginResource;
import io.bdeploy.ui.cli.RemotePluginTool.RemotePluginConfig;

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
            table.column(new DataTableColumn.Builder("ID").setMinWidth(25).build());
            table.column(new DataTableColumn.Builder("Name").setMinWidth(20).build());
            table.column(new DataTableColumn.Builder("Version").setMinWidth(7).build());
            table.column(new DataTableColumn.Builder("Loaded").setMinWidth(6).build());
            table.column(new DataTableColumn.Builder("Global").setMinWidth(6).build());

            for (PluginInfoDto dto : loaded) {
                table.row().cell(dto.id).cell(dto.name).cell(dto.version).cell("*").cell(dto.global ? "*" : "").build();
            }

            for (PluginInfoDto dto : notLoaded) {
                table.row().cell(dto.id).cell(dto.name).cell(dto.version).cell("").cell(dto.global ? "*" : "").build();
            }
            return table;
        } else if (config.add() != null) {
            try {
                return addPlugin(client, config.add(), config.replace());
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

    private DataResult addPlugin(PluginResource client, String file, boolean replace) throws IOException {
        Path plugin = Paths.get(file);
        if (!Files.isRegularFile(plugin)) {
            return createResultWithErrorMessage("Not a file: " + file);
        }

        try (InputStream is = Files.newInputStream(plugin);
                FormDataMultiPart fdmp = FormDataHelper.createMultiPartForStream("plugin", is)) {

            PluginInfoDto pid = client.uploadGlobalPlugin(fdmp, replace);

            return createSuccess().addField("Plugin Name", pid.name).addField("Plugin Version", pid.version).addField("Plugin ID",
                    pid.id);
        }
    }

}

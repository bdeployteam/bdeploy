package io.bdeploy.minion.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.plugin.PluginHeader;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemotePluginTool.RemotePluginConfig;
import io.bdeploy.ui.api.PluginResource;

@Help("Investigate a remote master's plugins")
@CliName("remote-plugin")
public class RemotePluginTool extends RemoteServiceTool<RemotePluginConfig> {

    private static final String PLUGIN_STATUS_FORMAT = "%1$-41s %2$-30s %3$-10s %4$-7s %5$-7s";

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
    protected void run(RemotePluginConfig config, RemoteService svc) {
        PluginResource client = ResourceProvider.getResource(svc, PluginResource.class, null);

        if (config.list()) {
            List<PluginInfoDto> loaded = client.getLoadedPlugins();
            List<PluginInfoDto> notLoaded = client.getNotLoadedGlobalPlugin();

            out().println(String.format(PLUGIN_STATUS_FORMAT, "ID", "Name", "Version", "Loaded", "Global"));
            for (PluginInfoDto dto : loaded) {
                out().println(String.format(PLUGIN_STATUS_FORMAT, dto.id, dto.name, dto.version, "*", dto.global ? "*" : ""));
            }

            for (PluginInfoDto dto : notLoaded) {
                out().println(String.format(PLUGIN_STATUS_FORMAT, dto.id, dto.name, dto.version, "", dto.global ? "*" : ""));
            }
        } else if (config.add() != null) {
            try {
                addPlugin(svc, config.add(), config.replace());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot add plugin", e);
            }
        } else if (config.unload() != null) {
            client.unloadPlugin(ObjectId.parse(config.unload()));
        } else if (config.load() != null) {
            client.loadGlobalPlugin(ObjectId.parse(config.load()));
        } else if (config.remove() != null) {
            client.deleteGlobalPlugin(ObjectId.parse(config.remove()));
        }
    }

    private void addPlugin(RemoteService svc, String file, boolean replace) throws IOException {
        Path plugin = Paths.get(file);
        if (!Files.isRegularFile(plugin)) {
            out().println("Not a file: " + file);
        }

        try (InputStream is = Files.newInputStream(plugin)) {
            // throws in case this is not a valid plugin
            PluginHeader hdr = PluginHeader.read(is);
            out().println("Adding plugin " + hdr.name + " " + hdr.version);
        }

        try (InputStream is = Files.newInputStream(plugin)) {
            MultiPart mp = new MultiPart();
            StreamDataBodyPart bp = new StreamDataBodyPart("plugin", is);
            bp.setFilename("plugin.jar");
            bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            mp.bodyPart(bp);

            WebTarget target = JerseyClientFactory.get(svc).getBaseTarget().path("/plugin-admin/upload-global");
            if (replace) {
                target = target.queryParam("replace", true);
            }
            Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new IllegalStateException("Upload failed: " + response.getStatusInfo().getStatusCode() + ": "
                        + response.getStatusInfo().getReasonPhrase());
            }
        }
    }

}

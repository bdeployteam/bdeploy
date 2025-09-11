package io.bdeploy.ui.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.cli.RemoteMasterTool.RemoteMasterConfig;

@Help("Investigate a remote master minion")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-master")
public class RemoteMasterTool extends RemoteServiceTool<RemoteMasterConfig> {

    public @interface RemoteMasterConfig {

        @Help(value = "List available minions", arg = false)
        boolean minions() default false;

        @Help("Path to an updated distribution (ZIP) which will be pushed to the master for update")
        String update();

        @Help("Path to a launcher distribution (ZIP) which will be pushed to the master")
        String launcher();

        @Help(value = "Restart the server", arg = false)
        boolean restart() default false;

        @Help(value = "Shutdown the server. ATTENTION: this will not restart the server.")
        boolean shutdown() default false;

        @Help("The minion to restart or shutdown. Manipulates the master if not given.")
        String minion();

        @Help(value = "Don't ask for confirmation when running potentially harmful actions.", arg = false)
        boolean yes() default false;
    }

    public RemoteMasterTool() {
        super(RemoteMasterConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteMasterConfig config, RemoteService svc) {
        if (config.minions()) {
            return listMinions(svc);
        } else if (config.update() != null) {
            Path zip = Paths.get(config.update());
            if (!Files.isRegularFile(zip)) {
                out().println(zip + " does not seem to be an update package");
            }

            return performUpdate(config, svc, zip);
        } else if (config.launcher() != null) {
            Path zip = Paths.get(config.launcher());
            if (!Files.isReadable(zip)) {
                out().println(zip + " does not seem to be a launcher package");
            }

            return pushLauncher(svc, zip);
        } else if (config.restart()) {
            return restartServer(config, svc);
        } else if (config.shutdown()) {
            return shutdownServer(config, svc);
        } else {
            return createNoOp();
        }
    }

    private RenderableResult restartServer(RemoteMasterConfig config, RemoteService svc) {
        try {
            String toRestart = config.minion();
            if (toRestart == null) {
                toRestart = "master";
            }
            if (!config.yes()) {
                System.out.println("Confirm restarting " + toRestart + " using Enter, or abort using CTRL+C");
                System.in.read();
            }

            if (config.minion() == null) {
                SoftwareUpdateResource swup = ResourceProvider.getResource(svc, SoftwareUpdateResource.class, getLocalContext());
                swup.restartServer();
            } else {
                NodeManagementResource nmr = ResourceProvider.getResource(svc, NodeManagementResource.class, getLocalContext());
                nmr.restartNode(toRestart);
            }
            return createSuccess();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query user response", e);
        }
    }

    private RenderableResult shutdownServer(RemoteMasterConfig config, RemoteService svc) {
        try {
            String toShutdown = config.minion();
            if (toShutdown == null) {
                BackendInfoResource bir = ResourceProvider.getResource(svc, BackendInfoResource.class, getLocalContext());
                Map<String, MinionStatusDto> minions = bir.getNodeStatus();

                var master = minions.entrySet().stream().filter(m -> m.getValue().config.master).findFirst();
                if (master.isEmpty()) {
                    throw new IllegalStateException("Cannot determine the master minion");
                }

                toShutdown = master.get().getKey();
            }

            if (!config.yes()) {
                System.out.println("Confirm shutting down " + toShutdown + " using Enter, or abort using CTRL+C");
                System.in.read();
            }

            NodeManagementResource nmr = ResourceProvider.getResource(svc, NodeManagementResource.class, getLocalContext());
            nmr.shutdownNode(toShutdown);

            return createSuccess();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query user response", e);
        }
    }

    private DataTable listMinions(RemoteService svc) {
        BackendInfoResource bir = ResourceProvider.getResource(svc, BackendInfoResource.class, getLocalContext());
        Map<String, MinionStatusDto> minions = bir.getNodeStatus();

        DataTable table = createDataTable();
        table.setCaption("Minions on " + svc.getUri());
        table.column(new DataTableColumn.Builder("Name").setMinWidth(13).build());
        table.column(new DataTableColumn.Builder("Type").setMinWidth(7).build());
        table.column(new DataTableColumn.Builder("Status").setMinWidth(6).build());
        table.column(new DataTableColumn.Builder("Start").setMinWidth(10).build());
        table.column(new DataTableColumn.Builder("OS").setMinWidth(7).build());
        table.column(new DataTableColumn.Builder("Version").setMinWidth(7).build());

        for (Map.Entry<String, MinionStatusDto> e : minions.entrySet()) {
            MinionStatusDto s = e.getValue();
            String startTime = s.startup != null ? FormatHelper.formatTemporal(s.startup) : "-";
            String status = s.offline ? "OFFLINE" : "ONLINE";
            MinionDto config = s.config;
            table.row().cell(e.getKey()).cell(e.getValue().config.minionNodeType.name()).cell(status).cell(startTime)
                    .cell(config.os).cell(config.version).build();
        }
        return table;
    }

    private DataResult performUpdate(RemoteMasterConfig config, RemoteService svc, Path zip) {
        try {
            Collection<Manifest.Key> keys = importAndPushUpdate(svc, zip, getActivityReporter());

            if (!config.yes()) {
                System.out.println("Pushing of update package successful, press any key to continue updating or CTRL+C to abort");
                System.in.read();
            }

            UpdateHelper.update(svc, keys, true, getLocalContext());
            return createSuccess();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process update", e);
        }
    }

    private DataResult pushLauncher(RemoteService svc, Path zip) {
        try {
            Collection<Key> keys = importAndPushUpdate(svc, zip, getActivityReporter());
            return createSuccess().addField("Keys", keys);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process launcher", e);
        }
    }

    /**
     * Import an update ZIP. BDeploy update ZIPs may carry nested launcher updates, which are imported as well.
     * <p>
     * The key of the BDeploy update is returned for further update purposes.
     */
    public static Collection<Manifest.Key> importAndPushUpdate(RemoteService remote, Path updateZipFile,
            ActivityReporter reporter) throws IOException {
        Path tmpDir = Files.createTempDirectory("update-");
        try {
            Path hive = tmpDir.resolve("hive");
            try (BHive tmpHive = new BHive(hive.toUri(), null, reporter)) {
                List<Manifest.Key> keys = UpdateHelper.importUpdate(updateZipFile, tmpDir.resolve("import"), tmpHive);
                PushOperation pushOp = new PushOperation().setRemote(remote);
                keys.forEach(pushOp::addManifest);
                tmpHive.execute(pushOp);

                return keys.stream().filter(UpdateHelper::isBDeployServerKey).toList();
            }
        } finally {
            PathHelper.deleteRecursiveRetry(tmpDir);
        }
    }

}

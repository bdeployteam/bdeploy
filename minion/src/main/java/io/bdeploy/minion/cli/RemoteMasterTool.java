package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteMasterTool.RemoteMasterConfig;

@Help("Investigate a remote master minion")
@CliName("remote-master")
public class RemoteMasterTool extends RemoteServiceTool<RemoteMasterConfig> {

    public @interface RemoteMasterConfig {

        @Help(value = "List available minions", arg = false)
        boolean minions()

        default false;

        @Help("Path to an updated distribution (ZIP) which will be pushed to the master for update")
        String update();

        @Help("Path to a launcher distribution (ZIP) which will be pushed to the master")
        String launcher();

        @Help(value = "Don't ask for confirmation before initiating the update process on the remote", arg = false)
        boolean yes() default false;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public RemoteMasterTool() {
        super(RemoteMasterConfig.class);
    }

    @Override
    protected void run(RemoteMasterConfig config, RemoteService svc) {
        MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class);

        if (config.minions()) {
            listMinions(client);
        } else if (config.update() != null) {
            Path zip = Paths.get(config.update());
            if (!Files.isRegularFile(zip)) {
                out().println(zip + " does not seem to be an update package");
            }

            performUpdate(config, svc, client, zip);
        } else if (config.launcher() != null) {
            Path zip = Paths.get(config.launcher());
            if (!Files.isReadable(zip)) {
                out().println(zip + " does not seem to be a launcher package");
            }

            pushLauncher(config, svc, client, zip);
        }
    }

    private void listMinions(MasterRootResource client) {
        SortedMap<String, NodeStatus> minions = client.getMinions();
        out().println(String.format("%1$-20s %2$-8s %3$25s %4$-10s %5$-15s", "NAME", "STATUS", "START", "OS", "VERSION"));
        for (Map.Entry<String, NodeStatus> e : minions.entrySet()) {
            NodeStatus s = e.getValue();
            String startTime = (s == null ? "" : FORMATTER.format(s.startup));
            out().println(String.format("%1$-20s %2$-8s %3$25s %4$-10s %5$-15s", e.getKey(), s == null ? "OFFLINE" : "ONLINE",
                    startTime, s == null ? "" : s.os, s == null ? "" : s.version));
        }
    }

    private void performUpdate(RemoteMasterConfig config, RemoteService svc, MasterRootResource client, Path zip) {
        try {
            Manifest.Key key = importAndPushUpdate(svc, zip, getActivityReporter());

            if (!config.yes()) {
                System.out.println("Pushing of update package successful, press any key to continue updating or CTRL+C to abort");
                System.in.read();
            }

            client.update(key, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process update", e);
        }
    }

    private void pushLauncher(RemoteMasterConfig config, RemoteService svc, MasterRootResource client, Path zip) {
        try {
            Manifest.Key key = importAndPushUpdate(svc, zip, getActivityReporter());
            out().println("Pushed launcher as " + key);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process launcher", e);
        }
    }

    public static Manifest.Key importAndPushUpdate(RemoteService remote, Path updateZipFile, ActivityReporter reporter)
            throws IOException {
        Path tmpDir = Files.createTempDirectory("update-");
        try {
            Path hive = tmpDir.resolve("hive");
            try (BHive tmpHive = new BHive(hive.toUri(), reporter)) {
                Manifest.Key key = UpdateHelper.importUpdate(updateZipFile, tmpDir.resolve("import"), tmpHive);
                tmpHive.execute(new PushOperation().setRemote(remote).addManifest(key));
                return key;
            }
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

}

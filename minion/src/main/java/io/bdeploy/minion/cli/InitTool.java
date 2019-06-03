package io.bdeploy.minion.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.InitTool.InitConfig;
import io.bdeploy.ui.api.Minion;

@Help("Initialize a minion root directory")
@CliName("init")
public class InitTool extends ConfiguredCliTool<InitConfig> {

    public @interface InitConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        String root();

        @Help("Optional directory where to deploy applications to, defaults to root/deploy")
        String deployments();

        @Help("The official hostname that the minion will think of for itself")
        @EnvironmentFallback("HOSTNAME")
        String hostname();

        @Help("Path to the ZIP file you extracted this program from. Set to 'ignore' to skip. Be aware that this will cause an immediate update once connected to a remote master.")
        String dist();

        @Help("Write the access token to a token file instead of printing it on the console")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        String tokenFile();

        @Help("Port that the master will run on.")
        int port() default 7701;
    }

    public InitTool() {
        super(InitConfig.class);
    }

    @Override
    protected void run(InitConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.hostname(), "Missing --hostname");
        helpAndFailIfMissing(config.dist(), "Missing --dist, required for initial hive preparation");

        Path root = Paths.get(config.root());
        if (Files.isDirectory(root)) {
            helpAndFail("Root " + root + " already exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            mr.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("init").build());
            out().println("Initializing minion keys...");
            String pack = initMinionRoot(root, mr, config.hostname(), config.port(), config.deployments());

            if (config.tokenFile() != null) {
                Files.write(Paths.get(config.tokenFile()), pack.getBytes(StandardCharsets.UTF_8));
            } else {
                out().println(pack);
            }

            // import original version of minion into new bhive for future updates.
            if ("ignore".equals(config.dist())) {
                // explicitly skip importing the initial package
                return;
            } else {
                // same logic as remote update: push the content of the ZIP as new manifest to the local hive.
                RemoteMasterTool.importAndPushUpdate(new RemoteService(mr.getHiveDir().toUri()), Paths.get(config.dist()),
                        getActivityReporter());
            }
        } catch (Exception e) {
            out().println("Failed to initialize minion root.");
            e.printStackTrace(out());
        }
    }

    public static String initMinionRoot(Path root, MinionRoot mr, String hostname, int port, String deployments)
            throws Exception {
        MinionState state = mr.initKeys();

        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().setIssuedTo(System.getProperty("user.name"))
                .addCapability(ApiAccessToken.ADMIN_CAPABILITY).build();

        String pack = helper.createSignaturePack(aat, state.keystorePath, state.keystorePass);

        RemoteService master = new RemoteService(UriBuilder.fromUri("https://" + hostname + ":" + port + "/api").build(), pack);
        state.minions.put(Minion.DEFAULT_MASTER_NAME, master);

        state.officialName = hostname;
        state.port = port;
        state.cleanupSchedule = MinionRoot.DEFAULT_CLEANUP_SCHEDULE;

        state.deploymentDir = root.resolve("deploy");
        if (deployments != null) {
            state.deploymentDir = Paths.get(deployments);
        }

        mr.setState(state);
        PathHelper.mkdirs(state.deploymentDir);

        return pack;
    }

}

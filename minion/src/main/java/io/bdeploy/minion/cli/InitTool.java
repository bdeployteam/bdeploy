package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.common.cfg.Configuration.ConfigurationValueMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.Configuration.ValueMapping;
import io.bdeploy.common.cfg.HostnameValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.InitTool.InitConfig;
import io.bdeploy.minion.job.MasterCleanupJob;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;

@Help("Initialize a minion root directory")
@CliName("init")
public class InitTool extends ConfiguredCliTool<InitConfig> {

    public @interface InitConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(NonExistingPathValidator.class)
        String root();

        @Help("Optional directory where to deploy applications to, defaults to root/deploy")
        String deployments();

        @Help("The official hostname that the minion will think of for itself")
        @EnvironmentFallback("HOSTNAME")
        @Validator(HostnameValidator.class)
        String hostname();

        @Help("Path to the ZIP file you extracted this program from. Set to 'ignore' to skip. Be aware that this will cause an immediate update once connected to a remote master.")
        String dist();

        @Help("Write the access token to a token file instead of printing it on the console")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        @Validator(NonExistingPathValidator.class)
        String tokenFile();

        @Help("Internal update directory.")
        String updateDir();

        @Help("Port that the master will run on.")
        int port() default 7701;

        @Help("The target mode for the server [CENTRAL,MANAGED,STANDALONE,SLAVE]. A MANAGED server can only work with a central counterpart.")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        MinionMode mode();
    }

    public InitTool() {
        super(InitConfig.class);
    }

    @Override
    protected void run(InitConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.hostname(), "Missing --hostname");
        helpAndFailIfMissing(config.mode(), "Missing --mode");

        Path root = Paths.get(config.root());

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            mr.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("init").build());
            out().println("Initializing minion keys...");
            String pack = initMinionRoot(root, mr, config.hostname(), config.port(), config.deployments(), config.mode());

            if (config.tokenFile() != null) {
                Files.write(Paths.get(config.tokenFile()), pack.getBytes(StandardCharsets.UTF_8));
            } else {
                out().println(pack);
            }

            String dist = config.dist();

            if (dist == null && config.updateDir() != null) {
                // use the installation directory of the application as source for the initial import.
                Path updRoot = Paths.get(config.updateDir()).getParent();
                if (Files.exists(updRoot) && Files.exists(updRoot.resolve("version.properties"))) {
                    out().println(
                            "Using BDeploy distribution in " + updRoot + " for initial data import. Use --dist to override.");
                    dist = updRoot.toString();
                }
            }

            // import original version of minion into new bhive for future updates.
            if (!"ignore".equals(dist)) {
                // same logic as remote update: push the content of the ZIP as new manifest to the local hive.
                RemoteMasterTool.importAndPushUpdate(new RemoteService(mr.getHiveDir().toUri()), Paths.get(dist),
                        getActivityReporter());
            }
        } catch (Exception e) {
            out().println("Failed to initialize minion root.");
            e.printStackTrace(out());
        }
    }

    public static String initMinionRoot(Path root, MinionRoot mr, String hostname, int port, String deployments, MinionMode mode)
            throws GeneralSecurityException, IOException {
        MinionState state = mr.initKeys();

        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addCapability(ApiAccessToken.ADMIN_CAPABILITY).build();

        String pack = helper.createSignaturePack(aat, state.keystorePath, state.keystorePass);
        RemoteService remote = new RemoteService(UriBuilder.fromUri("https://" + hostname + ":" + port + "/api").build(), pack);

        MinionConfiguration minionConfiguration = new MinionConfiguration();
        minionConfiguration.addMinion(Minion.DEFAULT_NAME, MinionDto.create(mode != MinionMode.SLAVE, remote));

        MinionManifest minionMf = new MinionManifest(mr.getHive());
        minionMf.update(minionConfiguration);

        state.self = Minion.DEFAULT_NAME;
        state.officialName = hostname;
        state.port = port;
        state.cleanupSchedule = MasterCleanupJob.DEFAULT_CLEANUP_SCHEDULE;
        state.fullyMigratedVersion = VersionHelper.getVersion().toString();
        state.mode = mode;

        state.deploymentDir = root.resolve("deploy");
        if (deployments != null) {
            state.deploymentDir = Paths.get(deployments);
        }

        mr.setState(state);
        PathHelper.mkdirs(state.deploymentDir);

        return pack;
    }

}

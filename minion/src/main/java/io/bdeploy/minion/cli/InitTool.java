package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.cfg.Configuration.ConfigurationValueMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.Configuration.ValueMapping;
import io.bdeploy.common.cfg.HostnameValidator;
import io.bdeploy.common.cfg.NonExistingOrEmptyDirPathValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionDto.MinionNodeType;
import io.bdeploy.minion.ConnectivityChecker;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.InitTool.InitConfig;
import io.bdeploy.minion.job.MasterCleanupJob;
import io.bdeploy.minion.user.PasswordAuthentication;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.cli.RemoteMasterTool;
import io.bdeploy.ui.dto.NodeAttachDto;
import jakarta.ws.rs.core.UriBuilder;

@Help("Initialize a minion root directory")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("init")
public class InitTool extends ConfiguredCliTool<InitConfig> {

    public @interface InitConfig {

        @Help("Root directory to initialize, must not exist.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String root();

        @Help("Logging root directory. Will be created if it does not exist.")
        String logData();

        @Help("Optional directory where to deploy applications to, defaults to root/deploy")
        String deployments();

        @Help("The official hostname that the minion will think of for itself")
        @EnvironmentFallback("HOSTNAME")
        @Validator(HostnameValidator.class)
        String hostname();

        @Help("Path to the ZIP file you extracted this program from. Set to 'ignore' to skip. Be aware that this will cause an immediate update once connected to a remote master.")
        String dist();

        @Help("Write the access token to a token file instead of printing it on the console. This is only for automated testing.")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String tokenFile();

        @Help("Write the node identification file to the given path. This can be used to attach the node to a master.")
        @Validator(NonExistingPathValidator.class)
        String nodeIdentFile();

        @Help("Internal update directory.")
        @EnvironmentFallback("BDEPLOY_INTERNAL_UPDATEDIR")
        String updateDir();

        @Help("Port that the master will run on.")
        int port() default 7701;

        @Help("The target mode for the server [CENTRAL,MANAGED,STANDALONE,NODE]. A MANAGED server can only work with a central counterpart.")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        MinionMode mode();

        @Help("The type of the node to initialize in case --mode is set to NODE. Can be SERVER or MULTI.")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        MinionNodeType nodeType() default MinionNodeType.SERVER;

        @Help("The initial user's name to create. The initial user is always system administrator. Not required for nodes.")
        String initUser();

        @Help("The password for the initial user to create.")
        String initPassword();

        @Help(value = "Skip the check for a valid host/port configuration", arg = false)
        boolean skipConnectionCheck() default false;

        @Help(value = "Enable pooling. Currently defaults to false, will default to true in future releases.")
        boolean pooling() default false;

        @Help("An optional pool directory which will be used to pool common objects. Otherwise, the default pooling directory will be used (<root>/objpool) if pooling is enabled.")
        String pool();
    }

    private static final Logger log = LoggerFactory.getLogger(InitTool.class);

    public InitTool() {
        super(InitConfig.class);
    }

    @Override
    protected RenderableResult run(InitConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.hostname(), "Missing --hostname");
        helpAndFailIfMissing(config.mode(), "Missing --mode");

        if (config.mode() != MinionMode.NODE) {
            helpAndFailIfMissing(config.initUser(), "Missing --initUser");
            helpAndFailIfMissing(config.initPassword(), "Missing --initPassword");
            try {
                PasswordAuthentication.throwIfPasswordInvalid(config.initPassword().toCharArray());
            } catch (RuntimeException e) {
                return createResultWithErrorMessage(e.getMessage());
            }
        } else if (config.nodeType() == MinionNodeType.SERVER) {
            helpAndFailIfMissing(config.nodeIdentFile(), "Missing --nodeIdentFile");
        }

        Path root = Paths.get(config.root());
        DataResult result = createSuccess();
        result.addField("Root Path", root.toString());

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            mr.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("init").build());
            String pack = initMinionRoot(root, mr, config.hostname(), config.port(), config.deployments(), config.mode(),
                    config.nodeType(), config.skipConnectionCheck());

            if (config.mode() == MinionMode.NODE) {
                handleNode(config, mr);
            } else {
                try {
                    mr.getUsers().createLocalUser(config.initUser(), config.initPassword(),
                            Collections.singletonList(ScopedPermission.GLOBAL_ADMIN));
                } catch (Exception e) {
                    return createResultWithErrorMessage(e.getMessage());
                }

                result.addField("User Created", config.initUser());
            }

            if (config.tokenFile() != null) {
                Path tokenPath = Paths.get(config.tokenFile());
                Files.write(tokenPath, pack.getBytes(StandardCharsets.UTF_8));
                result.addField("Token File", tokenPath);
            }

            String logDataDir = config.logData();
            if (!StringHelper.isNullOrEmpty(logDataDir)) {
                Path logDataDirPath = Paths.get(logDataDir).toAbsolutePath().normalize();
                PathHelper.mkdirs(logDataDirPath);
                mr.modifyState(s -> s.logDataDir = logDataDirPath);
                result.addField("Logging directory", logDataDirPath);
            }

            result.addField("Mode", config.mode().name());

            String dist = config.dist();

            if (dist == null && config.updateDir() != null) {
                // use the installation directory of the application as source for the initial
                // import.
                Path updRoot = Paths.get(config.updateDir()).getParent();
                if (Files.exists(updRoot) && Files.exists(updRoot.resolve("version.properties"))) {
                    dist = updRoot.toString();
                }
            }

            // import original version of minion into new bhive for future updates.
            // this is not done for nodes, as they do not require it, and not importing
            // will speed up init a lot and save a lot of disc space as well.
            // one drawback: also the manifest for the running node version is not there,
            // so it might be pushed from the master later, but only when updating the master
            // to the version that is already running on the node if versions diverged.
            if (!"ignore".equals(dist) && config.mode() != MinionMode.NODE) {
                // same logic as remote update: push the content of the ZIP as new manifest to
                // the local hive.
                Collection<Key> keys = RemoteMasterTool.importAndPushUpdate(new RemoteService(mr.getHiveDir().toUri()),
                        Paths.get(dist), getActivityReporter());

                result.addField("Software Imported", keys);
            } else {
                log.debug("Skipping software import from {}", dist);
            }

            if (config.pooling()) {
                Path poolPath = config.pool() == null ? root.resolve("objpool") : Paths.get(config.pool());
                Path path = poolPath.toAbsolutePath().normalize();
                mr.modifyState(s -> s.poolDefaultPath = path);

                // enable pooling on the default hive.
                mr.getHive().enablePooling(poolPath, false);

                result.addField("Object Pool", path);
            }
        } catch (Exception e) {
            PathHelper.deleteRecursiveRetry(root);
            if (config.mode() != MinionMode.NODE) {
                if (config.tokenFile() != null) {
                    PathHelper.deleteIfExistsRetry(Path.of(config.tokenFile()));
                }
            } else if (config.nodeIdentFile() != null) {
                PathHelper.deleteIfExistsRetry(Path.of(config.nodeIdentFile()));
            }
            throw new IllegalStateException("Cannot initialize minion root", e);
        }

        return result;
    }

    public static String initMinionRoot(Path root, MinionRoot mr, String hostname, int port, String deployments, MinionMode mode,
            MinionNodeType nodeType, boolean skipCheck) throws GeneralSecurityException, IOException {
        MinionState state = mr.initKeys();

        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ScopedPermission.GLOBAL_ADMIN).build();

        String pack = helper.createSignaturePack(aat, state.keystorePath, state.keystorePass);
        RemoteService remote = new RemoteService(UriBuilder.fromUri("https://" + hostname + ":" + port + "/api").build(), pack);

        if (!skipCheck) {
            // abort in case the hostname/port combo does not work.
            ConnectivityChecker.checkOrThrow(remote);
        }

        MinionDto self;
        if (mode != MinionMode.NODE || nodeType == MinionNodeType.SERVER) {
            self = MinionDto.createServerNode(mode != MinionMode.NODE, remote);
        } else if (nodeType == MinionNodeType.MULTI_RUNTIME) {
            self = MinionDto.createMultiNodeRuntime(remote);
        } else {
            throw new IllegalArgumentException("Illegal mode/nodeType combination: " + mode + ", " + nodeType);
        }

        MinionConfiguration minionConfiguration = new MinionConfiguration();
        minionConfiguration.addMinion(Minion.DEFAULT_NAME, self);

        MinionManifest minionMf = new MinionManifest(mr.getHive());
        minionMf.update(minionConfiguration);

        state.self = Minion.DEFAULT_NAME;
        state.officialName = hostname;
        state.port = port;
        state.cleanupSchedule = MasterCleanupJob.DEFAULT_CLEANUP_SCHEDULE;
        state.fullyMigratedVersion = VersionHelper.getVersion().toString();
        state.mode = mode;
        state.nodeType = nodeType;

        state.deploymentDir = root.resolve("deploy");
        if (deployments != null) {
            state.deploymentDir = Paths.get(deployments);
        }

        mr.setState(state);
        PathHelper.mkdirs(state.deploymentDir);

        return pack;
    }

    private void handleNode(InitConfig config, MinionRoot mr) throws IOException {
        if (config.nodeType() == MinionNodeType.MULTI_RUNTIME) {
            // no ident file for multi nodes.
            log.debug("Not creating an ident file for multi-node.");
            return;
        }

        MinionConfiguration mc = new MinionManifest(mr.getHive()).read();
        MinionDto self = mc.getMinion(mr.getState().self);

        NodeAttachDto nad = new NodeAttachDto();
        nad.name = config.hostname();
        nad.sourceMode = MinionMode.NODE;
        nad.remote = self.remote;

        Files.write(Paths.get(config.nodeIdentFile()), StorageHelper.toRawBytes(nad));

        out().println("Node Identification written to " + config.nodeIdentFile());
        out().println("Use this file through the BDeploy CLI or UI to attach the node to a master server.");
    }
}

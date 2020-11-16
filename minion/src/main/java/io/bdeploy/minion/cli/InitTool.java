package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.cfg.Configuration.ConfigurationValueMapping;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.Configuration.ValueMapping;
import io.bdeploy.common.cfg.HostnameValidator;
import io.bdeploy.common.cfg.NonExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
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
import io.bdeploy.ui.cli.RemoteMasterTool;

@Help("Initialize a minion root directory")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
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

        @Help("Write the access token to a token file instead of printing it on the console. Only required for nodes.")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        @Validator(NonExistingPathValidator.class)
        String tokenFile();

        @Help("Internal update directory.")
        String updateDir();

        @Help("Port that the master will run on.")
        int port() default 7701;

        @Help("The target mode for the server [CENTRAL,MANAGED,STANDALONE,NODE]. A MANAGED server can only work with a central counterpart.")
        @ConfigurationValueMapping(ValueMapping.TO_UPPERCASE)
        MinionMode mode();

        @Help("The initial user's name to create. The initial user is always system administrator. Not required for nodes.")
        String initUser();

        @Help("The password for the initial user to create.")
        String initPassword();
    }

    public InitTool() {
        super(InitConfig.class);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected RenderableResult run(InitConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.hostname(), "Missing --hostname");
        helpAndFailIfMissing(config.mode(), "Missing --mode");

        if (config.mode() != MinionMode.NODE) {
            helpAndFailIfMissing(config.initUser(), "Missing --initUser");
            helpAndFailIfMissing(config.initPassword(), "Missing --initPassword");
        }

        if (config.mode() == MinionMode.SLAVE) {
            helpAndFail("Mode SLAVE no longer supported, use NODE instead.");
        }

        Path root = Paths.get(config.root());
        DataResult result = createSuccess();
        result.addField("Root Path", root.toString());

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            mr.getAuditor().audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("init").build());
            String pack = initMinionRoot(root, mr, config.hostname(), config.port(), config.deployments(), config.mode());

            if (config.tokenFile() != null) {
                Files.write(Paths.get(config.tokenFile()), pack.getBytes(StandardCharsets.UTF_8));
                result.addField("Token File", config.tokenFile());
            } else {
                if (config.mode() == MinionMode.NODE) {
                    out().println(pack);
                }
            }

            if (config.mode() != MinionMode.NODE) {
                mr.getUsers().createLocalUser(config.initUser(), config.initPassword(),
                        Collections.singletonList(ApiAccessToken.ADMIN_PERMISSION));

                result.addField("User Created", config.initUser());
            }

            result.addField("Mode", config.mode().name());

            String dist = config.dist();

            if (dist == null && config.updateDir() != null) {
                // use the installation directory of the application as source for the initial import.
                Path updRoot = Paths.get(config.updateDir()).getParent();
                if (Files.exists(updRoot) && Files.exists(updRoot.resolve("version.properties"))) {
                    dist = updRoot.toString();
                }
            }

            // import original version of minion into new bhive for future updates.
            if (!"ignore".equals(dist)) {
                // same logic as remote update: push the content of the ZIP as new manifest to the local hive.
                Collection<Key> keys = RemoteMasterTool.importAndPushUpdate(new RemoteService(mr.getHiveDir().toUri()),
                        Paths.get(dist), getActivityReporter());

                result.addField("Software Imported", keys);
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot initialize minion root", e);
        }

        try {
            UserPrincipal current = root.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            if (!Files.getOwner(root).getName().equals(current.getName())) {
                Files.setOwner(root, current);
            }
        } catch (IOException e) {
            return createResultWithMessage(
                    "Cannot set ownership. The directory is initialized but belonging to the wrong principal.").setException(e);
        }

        return result;
    }

    public static String initMinionRoot(Path root, MinionRoot mr, String hostname, int port, String deployments, MinionMode mode)
            throws GeneralSecurityException, IOException {
        MinionState state = mr.initKeys();

        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ApiAccessToken.ADMIN_PERMISSION).build();

        String pack = helper.createSignaturePack(aat, state.keystorePath, state.keystorePass);
        RemoteService remote = new RemoteService(UriBuilder.fromUri("https://" + hostname + ":" + port + "/api").build(), pack);

        MinionConfiguration minionConfiguration = new MinionConfiguration();
        minionConfiguration.addMinion(Minion.DEFAULT_NAME, MinionDto.create(mode != MinionMode.NODE, remote));

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

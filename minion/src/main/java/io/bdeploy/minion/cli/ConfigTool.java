package io.bdeploy.minion.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingDirectoryValidator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.ConnectivityChecker;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.ConfigTool.ConfigToolConfig;
import io.bdeploy.ui.api.MinionMode;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Manages storage locations.
 */
@Help("Manage minion basic configuration")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("config")
public class ConfigTool extends ConfiguredCliTool<ConfigToolConfig> {

    public @interface ConfigToolConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("Logging root directory")
        @Validator(ExistingDirectoryValidator.class)
        String logData();

        @Help("Changes the name under which the minion advertises (and contacts) itself.")
        String hostname();

        @Help("Changes the port on which the minion hosts its services.")
        int port() default -1;

        @Help("Changes the web session timeout, after which users need to re-login. Timeout is specified in hours.")
        int sessionTimeout() default -1;

        @Help("The target mode of the minion.")
        MinionMode mode();

        @Help(value = "Skip the check for a valid host/port configuration", arg = false)
        boolean skipConnectionCheck() default false;
    }

    public ConfigTool() {
        super(ConfigToolConfig.class);
    }

    @Override
    protected RenderableResult run(ConfigToolConfig config) {
        if (config.logData() == null && config.hostname() == null && config.port() == -1 && config.sessionTimeout() == -1
                && config.mode() == null) {
            return createNoOp();
        }

        helpAndFailIfMissing(config.root(), "Missing --root");

        DataResult result = createSuccess();

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            String newHostname = config.hostname();
            int newPort = config.port();
            boolean newHostnameSet = newHostname != null;
            boolean newPortSet = newPort != -1;

            if (newHostnameSet || newPortSet) {
                MinionManifest mm = new MinionManifest(r.getHive());
                MinionConfiguration cfg = mm.read();

                MinionDto minion = cfg.getMinion(r.getState().self);
                if (minion == null) {
                    throw new IllegalStateException("Cannot find my own remote configuration in minion manifest");
                }
                RemoteService oldRemote = minion.remote;

                String hostname = newHostnameSet ? newHostname : oldRemote.getUri().getHost();
                int port = newPortSet ? newPort : oldRemote.getUri().getPort();

                minion.remote = new RemoteService(UriBuilder.fromUri(oldRemote.getUri()).host(hostname).port(port).build(),
                        oldRemote.getAuthPack());

                if (!config.skipConnectionCheck()) {
                    // make sure the new setting works.
                    ConnectivityChecker.checkOrThrow(minion.remote);
                }

                mm.update(cfg);
                r.modifyState(s -> {
                    s.officialName = hostname;
                    s.port = port;
                });

                if (newHostnameSet) {
                    result.addField("Hostname", hostname);
                }
                if (newPortSet) {
                    result.addField("Port", port);
                }
            }

            MinionMode newMode = config.mode();
            if (newMode != null) {
                r.getAuditor()
                        .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("set-mode").build());

                MinionMode oldMode = r.getMode();

                if (oldMode == MinionMode.CENTRAL || oldMode == MinionMode.NODE) {
                    throw new UnsupportedOperationException("Cannot convert " + oldMode + " root to anything else");
                }

                if (newMode == MinionMode.CENTRAL || newMode == MinionMode.NODE) {
                    throw new UnsupportedOperationException("Cannot convert root to " + newMode + ".");
                }

                r.modifyState(s -> s.mode = config.mode());
                result.addField("Mode", newMode);
            }

            int newSessionTimeout = config.sessionTimeout();
            if (newSessionTimeout != -1) {
                r.modifyState(s -> s.webSessionTimeoutHours = newSessionTimeout);
                result.addField("Session timeout (hours)", newSessionTimeout);
            }

            String logDataDir = config.logData();
            if (!StringHelper.isNullOrEmpty(logDataDir)) {
                Path logDataDirPath = Paths.get(logDataDir).toAbsolutePath().normalize();
                r.modifyState(s -> s.logDataDir = logDataDirPath);
                result.addField("Logging directory", logDataDirPath);
            }
        }

        return result;
    }
}

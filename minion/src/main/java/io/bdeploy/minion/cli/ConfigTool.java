package io.bdeploy.minion.cli;

import java.nio.file.Paths;

import jakarta.ws.rs.core.UriBuilder;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.ConfigTool.ConfigToolConfig;
import io.bdeploy.ui.api.MinionMode;

/**
 * Manages storage locations.
 */
@Help("Manage minion basic configuration.")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("config")
public class ConfigTool extends ConfiguredCliTool<ConfigToolConfig> {

    public @interface ConfigToolConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("Changes the name under which the minion advertises (and contacts) itself.")
        String hostname();

        @Help("Changes the port on which the minion hosts its services.")
        int port() default -1;

        @Help("The target mode of the minion.")
        MinionMode mode();
    }

    public ConfigTool() {
        super(ConfigToolConfig.class);
    }

    @Override
    protected RenderableResult run(ConfigToolConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            if (config.hostname() != null || config.port() != -1) {
                MinionManifest mm = new MinionManifest(r.getHive());
                MinionConfiguration cfg = mm.read();

                MinionDto minion = cfg.getMinion(r.getState().self);
                if (minion == null) {
                    throw new IllegalStateException("Cannot find my own remote configuration in minion manifest");
                }
                RemoteService oldRemote = minion.remote;

                String hostname = config.hostname() == null ? oldRemote.getUri().getHost() : config.hostname();
                int port = config.port() == -1 ? oldRemote.getUri().getPort() : config.port();

                minion.remote = new RemoteService(UriBuilder.fromUri(oldRemote.getUri()).host(hostname).port(port).build(),
                        oldRemote.getAuthPack());

                mm.update(cfg);
                r.modifyState(s -> {
                    s.officialName = hostname;
                    s.port = port;
                });
            }
            if (config.mode() != null) {
                r.getAuditor()
                        .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("set-mode").build());

                MinionMode oldMode = r.getMode();
                MinionMode newMode = config.mode();

                if (oldMode == MinionMode.CENTRAL || oldMode == MinionMode.NODE) {
                    throw new UnsupportedOperationException("Cannot convert " + oldMode + " root to anything else");
                }

                if (newMode == MinionMode.CENTRAL || newMode == MinionMode.NODE) {
                    throw new UnsupportedOperationException("Cannot convert root to " + oldMode + ".");
                }

                r.modifyState(s -> s.mode = config.mode());
            }
        }
        return createSuccess();
    }

}

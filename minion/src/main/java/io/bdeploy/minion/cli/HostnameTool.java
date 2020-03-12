package io.bdeploy.minion.cli;

import java.nio.file.Paths;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.HostnameTool.HostnameConfig;

/**
 * Manages storage locations.
 */
@Help("Manage the minions hostname property.")
@CliName("hostname")
public class HostnameTool extends ConfiguredCliTool<HostnameConfig> {

    public @interface HostnameConfig {

        @Help("Root directory for the master minion. The minion will put all required things here.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();

        @Help("Changes the name under which the minion advertises (and contacts) itself.")
        String update();
    }

    public HostnameTool() {
        super(HostnameConfig.class);
    }

    @Override
    protected void run(HostnameConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        helpAndFailIfMissing(config.update(), "Missing --update");

        try (MinionRoot r = new MinionRoot(Paths.get(config.root()), getActivityReporter())) {
            MinionManifest mm = new MinionManifest(r.getHive());
            MinionConfiguration cfg = mm.read();

            MinionDto minion = cfg.getMinion(r.getState().self);
            if (minion == null) {
                throw new IllegalStateException("Cannot find my own remote configuration in minion manifest");
            }

            RemoteService oldRemote = minion.remote;
            minion.remote = new RemoteService(UriBuilder.fromUri(oldRemote.getUri()).host(config.update()).build(),
                    oldRemote.getAuthPack());

            mm.update(cfg);

            r.modifyState(s -> s.officialName = config.update());
        }
    }

}

package io.bdeploy.bhive.cli;

import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.PushTool.PushConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolDefaultVerbose;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.jersey.cli.RemoteServiceTool;

/**
 * Pushes a manifest with all required objects to a remote hive. The process
 * will first negotiate the set of objects which really need to be transfered to
 * keep data amounts to a minimum.
 * <p>
 * Pushing is done using a temporary additional partial hive contained in a zip
 * file.
 */
@Help("Push Manifest(s) to a remote BHive instance.")
@CliName("push")
@ToolDefaultVerbose(true)
public class PushTool extends RemoteServiceTool<PushConfig> {

    public @interface PushConfig {

        @Help("The local BHive")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help("The remote hive name. Set to 'default' to push to the 'default' hive on the remote.")
        @EnvironmentFallback("REMOTE_BHIVE")
        String target();

        @Help("Manifest(s) to push. May appear multiple times. Format is 'name:tag'")
        String[] manifest() default {};
    }

    public PushTool() {
        super(PushConfig.class);
    }

    @Override
    protected void run(PushConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");
        helpAndFailIfMissing(config.target(), "Missing --target");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            PushOperation op = new PushOperation().setRemote(svc).setHiveName(config.target());

            for (String m : config.manifest()) {
                Manifest.Key key = Manifest.Key.parse(m);
                op.addManifest(key);
            }

            TransferStatistics stats = hive.execute(op);

            out().println(String.format("Pushed %1$d manifests. %2$d of %3$d trees reused, %4$d objects sent (%5$s)",
                    stats.sumManifests, stats.sumTrees - stats.sumMissingTrees, stats.sumTrees, stats.sumMissingObjects,
                    UnitHelper.formatFileSize(stats.transferSize)));
        }
    }

}

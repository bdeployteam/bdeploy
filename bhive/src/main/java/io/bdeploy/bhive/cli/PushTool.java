package io.bdeploy.bhive.cli;

import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.PushTool.PushConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.ToolDefaultVerbose;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.DurationHelper;
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
@ToolCategory(BHiveCli.REMOTE_TOOLS)
@CliName("push")
@ToolDefaultVerbose(true)
public class PushTool extends RemoteServiceTool<PushConfig> {

    public @interface PushConfig {

        @Help("The local BHive")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
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
    protected RenderableResult run(PushConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");
        helpAndFailIfMissing(config.target(), "Missing --target");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            PushOperation op = new PushOperation().setRemote(svc).setHiveName(config.target());

            for (String m : config.manifest()) {
                Manifest.Key key = Manifest.Key.parse(m);
                op.addManifest(key);
            }

            TransferStatistics stats = hive.execute(op);

            DataResult result = createSuccess();
            result.addField("Number of Manifests", stats.sumManifests);
            result.addField("Number of reused Trees", stats.sumTrees - stats.sumMissingTrees);
            result.addField("Number of Objects", stats.sumMissingObjects);
            result.addField("Transfer size", UnitHelper.formatFileSize(stats.transferSize));
            result.addField("Duration", DurationHelper.formatDuration(stats.duration));

            return result;
        }
    }

}

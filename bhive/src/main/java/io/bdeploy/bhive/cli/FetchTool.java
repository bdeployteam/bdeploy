package io.bdeploy.bhive.cli;

import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.FetchTool.FetchConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
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
 * A tool to fetch one or more manifests with all required objects from a remote
 * hive to the local hive. Same as {@link PushTool} but in the reverse
 * direction.
 */
@Help("Fetch Manifest(s) from a remote BHive instance.")
@ToolCategory(BHiveCli.REMOTE_TOOLS)
@CliName("fetch")
@ToolDefaultVerbose(true)
public class FetchTool extends RemoteServiceTool<FetchConfig> {

    public @interface FetchConfig {

        @Help("The local BHive")
        @EnvironmentFallback("BHIVE")
        String hive();

        @Help("The remote hive name if not default")
        @EnvironmentFallback("REMOTE_BHIVE")
        String source();

        @Help("Manifest(s) to push. May appear multiple times. Format is 'name:tag'")
        String[] manifest() default {};
    }

    public FetchTool() {
        super(FetchConfig.class);
    }

    @Override
    protected RenderableResult run(FetchConfig config, RemoteService svc) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            FetchOperation op = new FetchOperation().setRemote(svc).setHiveName(config.source());

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

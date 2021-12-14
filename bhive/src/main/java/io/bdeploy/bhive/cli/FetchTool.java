package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.cli.FetchTool.FetchConfig;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.ToolDefaultVerbose;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
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
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
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

        Path path = Paths.get(config.hive());

        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter());
                Transaction t = hive.getTransactions().begin()) {
            FetchOperation op = new FetchOperation().setRemote(svc).setHiveName(config.source());

            for (String m : config.manifest()) {
                Manifest.Key key = Manifest.Key.parse(m);
                op.addManifest(key);
            }

            TransferStatistics stats = hive.execute(op);
            return stats.toResult(createSuccess());
        }
    }

}

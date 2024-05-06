package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedMap;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.PruneTool.PruneConfig;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.util.FormatHelper;

/**
 * A tool to remove unreferenced objects from a hive, for instance after
 * deleting a manifest.
 */
@Help("Prune any unreferenced objects from the given BHive")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("prune")
public class PruneTool extends ConfiguredCliTool<PruneConfig> {

    public @interface PruneConfig {

        @Help("The BHive to use")
        @EnvironmentFallback("BHIVE")
        @Validator({ ExistingPathValidator.class, PathOwnershipValidator.class })
        String hive();

        @Help(value = "List details about removed objects", arg = false)
        boolean verbose() default false;
    }

    public PruneTool() {
        super(PruneConfig.class);
    }

    @Override
    protected RenderableResult run(PruneConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        Path path = Paths.get(config.hive());

        try (BHive hive = new BHive(path.toUri(), getAuditorFactory().apply(path), getActivityReporter())) {
            SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());

            DataResult r = createSuccess();
            if (config.verbose()) {
                result.forEach((k, v) -> r.addField(k.toString(), FormatHelper.formatFileSize(v)));
            }
            long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();
            r.addField("Sum Objects Removed", Integer.toString(result.size()));
            r.addField("Sum Bytes Freed", FormatHelper.formatFileSize(sum));

            return r;
        }
    }

}

package io.bdeploy.bhive.cli;

import java.nio.file.Paths;
import java.util.SortedMap;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.cli.PruneTool.PruneConfig;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.util.UnitHelper;

/**
 * A tool to remove unreferenced objects from a hive, for instance after
 * deleting a manifest.
 */
@Help("Prune any unreferenced objects from the given BHive")
@CliName("prune")
public class PruneTool extends ConfiguredCliTool<PruneConfig> {

    public @interface PruneConfig {

        @Help("The BHive to use")
        String hive();

        @Help(value = "List details about removed objects", arg = false)
        boolean verbose() default false;
    }

    public PruneTool() {
        super(PruneConfig.class);
    }

    @Override
    protected void run(PruneConfig config) {
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());

            if (config.verbose()) {
                result.forEach((k, v) -> out().println(String.format("%1$s %2$s", k, UnitHelper.formatFileSize(v))));
            }
            long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();
            out().println(String.format("Removed %1$d objects (%2$s).", result.size(), UnitHelper.formatFileSize(sum)));
        }
    }

}
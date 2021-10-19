package io.bdeploy.launcher.cli.ui.browser;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.jersey.audit.Auditor;

/**
 * Executes the prune operation on all local hives.
 */
public class PruneTask extends HiveTask {

    public PruneTask(List<Path> hives, Auditor auditor) {
        super(hives, auditor);
    }

    @Override
    protected String getTaskName() {
        return "Pruning unreferenced objects in all hives.";
    }

    @Override
    protected void doExecute(BHive hive) {
        SortedMap<ObjectId, Long> result = hive.execute(new PruneOperation());
        long sum = result.values().stream().collect(Collectors.summarizingLong(x -> x)).getSum();

        builder.append("Sum Objects Removed: ").append(Integer.toString(result.size())).append("\n");
        builder.append("Sum Bytes Freed: ").append(Long.toString(sum)).append("\n");
        for (ObjectId object : result.keySet()) {
            builder.append("Removed: ").append(object);
        }
    }

}

package io.bdeploy.bhive.op.remote;

import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.util.DurationHelper;
import io.bdeploy.common.util.UnitHelper;

/**
 * Holds information about a transfer (push/fetch) operation.
 */
public class TransferStatistics {

    public long sumManifests;

    public long sumTrees;
    public long sumMissingTrees;

    public long sumMissingObjects;

    public long transferSize;

    public long duration;

    /**
     * Appends the statistics to the given result
     */
    public DataResult toResult(DataResult result) {
        result.addField("Number of Manifests", sumManifests);
        result.addField("Number of reused Trees", sumTrees - sumMissingTrees);
        result.addField("Number of Objects", sumMissingObjects);
        result.addField("Transfer size", UnitHelper.formatFileSize(transferSize));
        result.addField("Duration", DurationHelper.formatDuration(duration));
        return result;
    }

}

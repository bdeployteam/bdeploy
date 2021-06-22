package io.bdeploy.bhive.op.remote;

import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.util.FormatHelper;

/**
 * Holds information about a transfer (push/fetch) operation.
 */
public class TransferStatistics {

    public long sumManifests;

    public long sumTrees;
    public long sumMissingTrees;
    public long sumMissingObjects;

    /**
     * Number of bytes that have been transfered
     */
    public long transferSize;

    /**
     * Total duration in milliseconds
     */
    public long duration;

    /**
     * Appends the statistics to the given result
     */
    public DataResult toResult(DataResult result) {
        result.addField("Number of Manifests", sumManifests);
        result.addField("Number of reused Trees", sumTrees - sumMissingTrees);
        result.addField("Number of Objects", sumMissingObjects);
        result.addField("Transfer size", FormatHelper.formatFileSize(transferSize));
        result.addField("Duration", FormatHelper.formatDuration(duration));
        result.addField("Transfer speed", FormatHelper.formatTransferRate(duration, transferSize));
        return result;
    }

}

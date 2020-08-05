package io.bdeploy.bhive.op.remote;

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

}

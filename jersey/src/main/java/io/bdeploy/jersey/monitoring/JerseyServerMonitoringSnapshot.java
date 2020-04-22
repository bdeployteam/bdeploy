package io.bdeploy.jersey.monitoring;

public final class JerseyServerMonitoringSnapshot {

    public long snapshotTime;

    public long conAccepted;
    public long conClosed;
    public long conConnected;
    public long conErrored;
    public long conBytesRead;
    public long conBytesWritten;

    public long poolCoreSize;
    public long poolMaxSize;
    public long poolCurrentSize;
    public long poolExceeded;
    public long poolTasksQueued;
    public long poolTasksCancelled;
    public long poolTasksFinished;

    public long reqReceived;
    public long reqCompleted;
    public long reqTimedOut;
    public long reqCancelled;

    public long vmThreads;
    public long vmCpus;
    public long vmMaxMem;
    public long vmTotalMem;
    public long vmFreeMem;
}
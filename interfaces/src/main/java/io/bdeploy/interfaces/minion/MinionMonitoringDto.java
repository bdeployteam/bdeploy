package io.bdeploy.interfaces.minion;

import java.util.List;

/**
 * An information set of monitoring data about a minion.
 */
public class MinionMonitoringDto {

    /**
     * Timestamp of update;
     */
    public long timestamp;

    /**
     * Number of available processors
     */
    public int availableProcessors;

    /**
     * Load average values.
     * The loadAvg[0] is newest and taken at <code>timestamp</code>.
     */
    public List<Double> loadAvg;

    /**
     * The recent CPU usage on the system.
     */
    public List<Double> cpuUsage;
}

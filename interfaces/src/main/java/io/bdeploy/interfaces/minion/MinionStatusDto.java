package io.bdeploy.interfaces.minion;

import java.time.Instant;

/**
 * A DTO that contains the runtime status of a minion along with its {@linkplain MinionDto details}.
 */
public class MinionStatusDto {

    /**
     * Whether or nor this node is currently online.
     */
    public boolean offline;

    /**
     * Some additional text when the node is offline.
     */
    public String infoText;

    /**
     * Configuration details about this node.
     */
    public MinionDto config;

    /**
     * The startup time of the node
     */
    public Instant startup;

    /**
     * Transient monitoring information.
     */
    public MinionMonitoringDto monitoring;

    /**
     * Rolling average duration of the node manager contact call in milliseconds.
     */
    public long lastRoundtrip;

    /**
     * Whether or not the node is up to date with master in terms of installed and activated versions for every instance
     */
    public NodeSynchronizationStatus nodeSynchronizationStatus = NodeSynchronizationStatus.UNKNOWN;

    /**
     * Creates a new node status indicating that this node is offline.
     *
     * @param config
     *            the configuration of the minion
     * @param infoText
     *            the reason why the node could not be contacted
     */
    public static MinionStatusDto createOffline(MinionDto config, String infoText) {
        MinionStatusDto status = new MinionStatusDto();
        status.config = config;
        status.offline = true;
        status.infoText = infoText;
        status.nodeSynchronizationStatus = NodeSynchronizationStatus.NOT_SYNCHRONIZED;
        return status;
    }

    public static MinionStatusDto createMulti(MinionDto config, int runtimeCount) {
        MinionStatusDto status = new MinionStatusDto();
        status.config = config;
        status.offline = true; // always offline, there is nothing to talk to.
        status.startup = Instant.ofEpochMilli(0); // that never starts up.
        status.infoText = runtimeCount <= 0 ? "Waiting for runtime nodes..." : ("Attached runtime nodes: " + runtimeCount);
        status.nodeSynchronizationStatus = NodeSynchronizationStatus.SYNCHRONIZED; // always.
        return status;
    }

}

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
        return status;
    }

}
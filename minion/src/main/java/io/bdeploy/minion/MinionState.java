package io.bdeploy.minion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.ui.api.MinionMode;

/**
 * Represents the persistent state of a minion
 */
public class MinionState {

    /**
     * The own minion's name.
     */
    public String self;

    /**
     * The minions mode.
     * <p>
     * Defaults to {@link MinionMode#STANDALONE} for compatibility.
     */
    public MinionMode mode = MinionMode.STANDALONE;

    /**
     * The mode of the node. This can be either SERVER (classic node) or MULTI for now. Defaults to SERVER for compatibility with
     * all existing nodes.
     */
    public MinionDto.MinionNodeType nodeType = MinionDto.MinionNodeType.SERVER;

    /**
     * Path to the keystore containing the private key and certificates for the
     * minion
     */
    public Path keystorePath;

    /**
     * Optional secondary keystore containing information for HTTPS *only*.
     */
    public Path keystoreHttpsPath;

    /**
     * Passphrase for the keystore
     */
    public char[] keystorePass;

    /**
     * The "official" host name of this minion. This is used to expand variables in
     * configuration.
     */
    public String officialName;

    /**
     * The port to listen on.
     */
    public int port;

    /**
     * Directory where deployments will be put to.
     */
    public Path deploymentDir;

    /**
     * Storage directories hosting hives.
     */
    public List<Path> storageLocations = new ArrayList<>();

    /**
     * Minimum available disc space (in bytes) on working directories.
     */
    public Long storageMinFree = 1L * 1024 * 1024 * 1024;

    /**
     * 'Cron' format schedule for cleanup job.
     */
    public String cleanupSchedule;

    /**
     * Timestamp of last successful cleanup execution on this minion (only relevant for master).
     */
    public long cleanupLastRun;

    /**
     * 'Cron' format schedule for LDAP Synchronization Job.
     */
    public String ldapSyncSchedule;

    /**
     * Timestamp of last successful execution of LDAP Synchronization Job on this minion.
     */
    public long ldapSyncLastRun;

    /**
     * Timestamp of last successful cleanup downloads dir execution on this minion.
     */
    public long cleanupDownloadsDirLastRun;

    /**
     * Timestamp of last successful execution of Check Latest GitHub Release Job on this minion.
     */
    public long checkLatestGitHubReleaseLastRun;

    /**
     * The last minion version which was successfully migrated to.
     */
    public String fullyMigratedVersion;

    /**
     * The last known {@link ObjectId} of the built-in logging configuration.
     */
    public ObjectId logConfigId;

    /**
     * The timeout of web sessions in hours. Defaults to a week.
     */
    public Integer webSessionTimeoutHours = 24 * 7;

    /**
     * The timeout of web sessions within which a user needs to be active to have the session be kept alive.
     */
    public Integer webSessionActiveTimeoutHours = 24;

    /**
     * Amount of times an object needs to be referenced for it to be moved to a global pool.
     */
    public Integer poolUsageThreshold = 2;

    /**
     * Schedule for the pool re-organization job.
     */
    public String poolOrganizationSchedule;

    /**
     * Timestamp of last pool re-organization.
     */
    public long poolOrganizationLastRun;

    /**
     * The default pool path which will be set automatically on new BHives created by the server. Existing BHives need to be setup
     * manually or if setup continue to use the already configured path.
     */
    public Path poolDefaultPath;

    /**
     * A {@link Path} which points to the log data directory.
     */
    public Path logDataDir;
}

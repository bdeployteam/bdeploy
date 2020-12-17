package io.bdeploy.ui.api;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.jersey.audit.Auditor;

/**
 * Represents a node.
 */
public interface Minion {

    /**
     * The default name of each node. To determine whether a node is a master, use the provided {@link #isMaster() API} and never
     * the name.
     */
    public static final String DEFAULT_NAME = "master";

    /**
     * Returns the directory where the minion stores temporary files that are served to the client.
     */
    public Path getDownloadDir();

    /**
     * Returns a directory which is suitable to place temporary directories/files within.
     */
    public Path getTempDir();

    /**
     * Returns the configuration of this minion
     */
    public MinionDto getMinionConfig();

    /**
     * Retrieve registered nodes. This makes only sense if the current VM hosts a master, otherwise only 'self' is returned.
     */
    public MinionConfiguration getMinions();

    /**
     * Creates and returns a new weak token for the given principal. The weak token
     * is only suitable for fetching fetching by launcher-like applications.
     *
     * @param principal the principal name to issue the token to.
     * @return a token (authentication pack including certificate).
     */
    public String createWeakToken(String principal);

    /**
     * Creates and returns a new token for the given principal having the given global permissions.
     *
     * @param principal the principal name to issue the token to.
     * @param permissions the global permissions
     * @return a token (authentication pack including certificate).
     */
    public String createToken(String principal, Collection<ScopedPermission> permissions, boolean full);

    /**
     * @return the mode the hosting minion is run in.
     */
    public MinionMode getMode();

    /**
     * @return the own {@link RemoteService} for loop-back communication
     */
    public RemoteService getSelf();

    /**
     * @return the hostname used to init the minion's root.
     */
    public String getHostName();

    /**
     * @return the {@link Auditor} responsible for this Minion.
     */
    public Auditor getAuditor();

    /**
     * Returns whether or not the minion represents the master.
     */
    public boolean isMaster();

    /**
     * Encrypts and signs the given payload using the minions private keys, embedding the public certificate used for encryption
     * in the payload.
     */
    public <T> String getEncryptedPayload(T payload);

    /**
     * Descrypts a payload encrypted using {@link #getEncryptedPayload(Object)}.
     */
    public <T> T getDecryptedPayload(String encrypted, Class<T> clazz);

    /**
     * Signs the given windows executable with the certificate of the minion
     *
     * @param executable
     *            the executable to sign.
     * @param appName
     *            the name that will be embedded in the signature.
     * @param appUrl
     *            the URL that will be embedded in the signature.
     */
    public void signExecutable(File executable, String appName, String appUrl);

    /**
     * @return the {@link PluginManager} for the minion.
     */
    public PluginManager getPluginManager();

    /**
     * @return the minion settings.
     */
    public SettingsConfiguration getSettings();

    /**
     * Updates the minion settings.
     *
     * @param settings the new settings.
     */
    public void setSettings(SettingsConfiguration settings);

}

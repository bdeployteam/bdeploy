package io.bdeploy.ui.dto;

import java.util.Collection;
import java.util.Collections;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.Version;

/**
 * Stores the required manifest keys that must be transfered and installed on the target.
 */
public class MinionUpdateDto {

    /**
     * The version that will be installed
     */
    public Version updateVersion;

    /**
     * The version that is currently running.
     */
    public Version runningVersion;

    /**
     * Flag whether or not updates can be installed.
     */
    public boolean updateAvailable;

    /**
     * Flag whether an update must be installed.
     */
    public boolean forceUpdate;

    /**
     * The packages to install
     */
    public Collection<Key> packagesToInstall = Collections.emptyList();

    /**
     * The missing packages that must be transfered
     */
    public Collection<Key> packagesToTransfer = Collections.emptyList();

}

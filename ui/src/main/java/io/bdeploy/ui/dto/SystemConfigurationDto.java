package io.bdeploy.ui.dto;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;

public class SystemConfigurationDto {

    /**
     * The minion this system resides on - only set on CENTRAL
     */
    public String minion;

    /**
     * The key of the configuration when read from the {@link BHive}. This is ignored when creating or updating systems.
     */
    public Manifest.Key key;

    /**
     * The actual {@link SystemConfiguration} containing all configuration data.
     */
    public SystemConfiguration config;

}

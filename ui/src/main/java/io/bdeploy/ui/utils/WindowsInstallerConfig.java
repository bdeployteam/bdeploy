package io.bdeploy.ui.utils;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

/**
 * A DTO describing the data that is embedded into the native Windows executable.
 * <p>
 * <b>ATTENTION:</b> Renaming fields in here will break the deserialization in the C# application.
 * Adding optional fields is not an issue. They are ignored.
 * </p>
 */
public class WindowsInstallerConfig {

    /**
     * The URL as well as the token to access the minion.
     */
    public RemoteService remoteService;

    /**
     * The URL to download the launcher
     */
    public String launcherUrl;

    /**
     * Optional URL to download the splash of the application
     */
    public String splashUrl;

    /**
     * Optional URL to download the icon of the application
     */
    public String iconUrl;

    /**
     * The serialized {@linkplain ClickAndStartDescriptor}.
     */
    public String applicationJson;

    /**
     * Instance group name
     */
    public String instanceGroupName;

    /**
     * Instance name
     */
    public String instanceName;

    /**
     * Human readable name of the application
     */
    public String applicationName;

    /**
     * The unique identifier of the application
     */
    public String applicationUid;

    /**
     * Human readable name of the product vendor.
     */
    public String productVendor;

}

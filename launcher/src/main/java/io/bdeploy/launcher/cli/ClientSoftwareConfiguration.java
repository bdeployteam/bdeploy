package io.bdeploy.launcher.cli;

import java.util.ArrayList;
import java.util.Collection;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

/**
 * Stores software used by a given application.
 */
public class ClientSoftwareConfiguration {

    /**
     * The key of the launcher required for this software.
     * <p>
     * This key is ONLY written in case that launching of the application is delegated to another launcher. This information is
     * used during cleanup so that the launcher is kept as long as at least one software manifest is referencing it.
     * </p>
     *
     * @deprecated The delegate launcher removal in 7.2.0 made this field obsolete. The only reason that it remains for now, is so
     *             that the uninstallation of old client applications can continue to work properly.
     */
    @Deprecated(since = "7.2.0")
    public Manifest.Key launcher;

    /**
     * The descriptor that was used to launch the application.
     */
    public ClickAndStartDescriptor clickAndStart;

    /**
     * Additional human-readable information. Only present if we are able to communicate with the remote server. If the
     * server runs in a version that is too old then this DTO will be null.
     */
    public ClientApplicationDto metadata;

    /**
     * Snapshot of clientAppCfg from last installation. Allows the application to start offline when the server is not reachable.
     */
    public ClientApplicationConfiguration clientAppCfg;

    /**
     * The software that is required by the application.
     */
    public Collection<Manifest.Key> requiredSoftware = new ArrayList<>();
}

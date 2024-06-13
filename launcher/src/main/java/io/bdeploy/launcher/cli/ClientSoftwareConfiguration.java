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
     */
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
     * Snapshot of clientAppCfg from last installation.
     * Used when server is not reachable to start application offline.
     */
    public ClientApplicationConfiguration clientAppCfg;

    /**
     * The software that is required by the application.
     */
    public Collection<Manifest.Key> requiredSoftware = new ArrayList<>();
}

package io.bdeploy.launcher.cli;

import java.util.ArrayList;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
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
     * The key of the {@link ClientSoftwareConfiguration} in the {@link BHive}.
     */
    @JsonIgnore
    public Manifest.Key key;

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
     * The software that is required by the application.
     */
    public Collection<Manifest.Key> requiredSoftware = new ArrayList<>();
}

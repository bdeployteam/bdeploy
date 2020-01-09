package io.bdeploy.launcher.cli;

import java.util.ArrayList;
import java.util.Collection;

import io.bdeploy.bhive.model.Manifest;

/**
 * Stores which software is used by a given application.
 */
public class ClientSoftwareConfiguration {

    /**
     * The software that is required by the application.
     */
    public Collection<Manifest.Key> requiredSoftware = new ArrayList<>();

}

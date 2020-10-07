package io.bdeploy.interfaces.configuration.instance;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

/**
 * Holds required information for a launcher to be able to install and launch an application.
 */
public class ClientApplicationConfiguration {

    public ApplicationConfiguration appConfig;

    public ApplicationDescriptor appDesc;

    public InstanceNodeConfiguration instanceConfig;

    public String activeTag;

    public byte[] clientSplashData;

    public byte[] clientImageIcon;

    public List<Manifest.Key> resolvedRequires = new ArrayList<>();

}

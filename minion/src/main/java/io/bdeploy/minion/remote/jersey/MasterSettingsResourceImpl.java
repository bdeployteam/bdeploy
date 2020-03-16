package io.bdeploy.minion.remote.jersey;

import javax.inject.Inject;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.manifest.SettingsManifest;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.minion.MinionRoot;

public class MasterSettingsResourceImpl implements MasterSettingsResource {

    @Inject
    private MinionRoot root;

    @Override
    public SettingsConfiguration getAuthenticationSettings() {
        return SettingsManifest.read(root.getHive(), root.getEncryptionKey(), true);
    }

    @Override
    public void setAuthenticationSettings(SettingsConfiguration settings) {
        SettingsManifest.write(root.getHive(), settings, root.getEncryptionKey());
    }

}

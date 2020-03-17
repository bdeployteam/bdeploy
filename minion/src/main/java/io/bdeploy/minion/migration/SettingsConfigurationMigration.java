package io.bdeploy.minion.migration;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.manifest.SettingsManifest;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.minion.MinionRoot;

public class SettingsConfigurationMigration {

    private static final Logger log = LoggerFactory.getLogger(SettingsConfigurationMigration.class);

    private SettingsConfigurationMigration() {
    }

    public static void run(MinionRoot root) throws GeneralSecurityException, IOException {
        SettingsConfiguration settings = SettingsManifest.read(root.getHive(), root.getEncryptionKey(), false);

        boolean changes = false;
        for (LDAPSettingsDto lds : settings.auth.ldapSettings) {
            if (lds.id == null) {
                lds.id = UuidHelper.randomId();
                changes = true;
            }
        }
        if (changes) {
            SettingsManifest.write(root.getHive(), settings, root.getEncryptionKey());
            log.info("LDAP settings updated.");
        }
    }

}

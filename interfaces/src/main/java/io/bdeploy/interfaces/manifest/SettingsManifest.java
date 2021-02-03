package io.bdeploy.interfaces.manifest;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;

public class SettingsManifest {

    private static final Logger log = LoggerFactory.getLogger(SettingsManifest.class);

    private static final String SETTINGS_MANIFEST = "meta/settings";
    private static final String CONFIG_FILE = "settings.json";

    private SettingsManifest() {
    }

    public static SettingsConfiguration read(BHive hive, SecretKeySpec key, boolean clearPasswords) {
        Optional<Long> tag = hive.execute(new ManifestMaxIdOperation().setManifestName(SETTINGS_MANIFEST));
        if (!tag.isPresent()) {
            return new SettingsConfiguration();
        }

        Manifest mf = hive
                .execute(new ManifestLoadOperation().setManifest(new Manifest.Key(SETTINGS_MANIFEST, tag.get().toString())));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(CONFIG_FILE))) {
            return decryptOrClearPasswords(StorageHelper.fromStream(is, SettingsConfiguration.class), key, clearPasswords);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load minion settings", e);
        }
    }

    public static void write(BHive hive, SettingsConfiguration config, SecretKeySpec key) {
        try (Transaction t = hive.getTransactions().begin()) {
            Long tag = hive.execute(new ManifestMaxIdOperation().setManifestName(SETTINGS_MANIFEST)).orElse(1l);
            Manifest.Builder builder = new Manifest.Builder(new Manifest.Key(SETTINGS_MANIFEST, Long.toString(tag + 1)));
            Tree.Builder tree = new Tree.Builder();

            tree.add(new Tree.Key(CONFIG_FILE, EntryType.BLOB), hive
                    .execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(encryptPasswords(hive, config, key)))));

            hive.execute(new InsertManifestOperation()
                    .addManifest(builder.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tree))).build(hive)));

            hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(SETTINGS_MANIFEST));
        }
    }

    private static SettingsConfiguration decryptOrClearPasswords(SettingsConfiguration config, SecretKeySpec key,
            boolean clearPasswords) {
        for (LDAPSettingsDto lds : config.auth.ldapSettings) {
            if (clearPasswords) {
                lds.pass = null;
            } else if (lds.pass != null && !lds.pass.isEmpty()) {
                try {
                    lds.pass = SecurityHelper.decrypt(lds.pass, key);
                } catch (GeneralSecurityException e) {
                    log.error("Cannot decrypt password for {}", lds.server, e);
                }
            } else {
                log.warn("No password for {}", lds.server);
            }
        }
        return config;
    }

    private static SettingsConfiguration encryptPasswords(BHive hive, SettingsConfiguration config, SecretKeySpec key) {
        SettingsConfiguration oldConfig = read(hive, key, false);
        for (LDAPSettingsDto lds : config.auth.ldapSettings) {
            if (lds.pass == null || lds.pass.isEmpty()) {
                reApplyOldPassword(key, oldConfig, lds);
            } else {
                try {
                    lds.pass = SecurityHelper.encrypt(lds.pass, key);
                } catch (GeneralSecurityException e) {
                    throw new IllegalStateException("Cannot encrypt password for server: " + lds.server, e);
                }
            }
        }
        return config;
    }

    private static void reApplyOldPassword(SecretKeySpec key, SettingsConfiguration oldConfig, LDAPSettingsDto lds) {
        LDAPSettingsDto oldLds = oldConfig.auth.ldapSettings.stream().filter(l -> l.id.equals(lds.id)).findAny().orElse(null);
        if (oldLds != null) {
            try {
                lds.pass = SecurityHelper.encrypt(oldLds.pass, key);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Cannot encrypt password for server: " + lds.server, e);
            }
        } else {
            throw new IllegalStateException("No existing password found, and no password supplied for LDAP server " + lds.server);
        }
    }

}

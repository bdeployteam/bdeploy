package io.bdeploy.interfaces.manifest;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
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
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.interfaces.settings.MailReceiverSettingsDto;
import io.bdeploy.interfaces.settings.MailSenderSettingsDto;

public class SettingsManifest {

    private static final Logger log = LoggerFactory.getLogger(SettingsManifest.class);

    private static final String SETTINGS_MANIFEST = "meta/settings";
    private static final String CONFIG_FILE = "settings.json";

    private SettingsManifest() {
    }

    public static SettingsConfiguration read(BHiveExecution hive, SecretKeySpec key, boolean clearPasswords) {
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
            Long tag = hive.execute(new ManifestMaxIdOperation().setManifestName(SETTINGS_MANIFEST)).orElse(1L);
            Manifest.Builder builder = new Manifest.Builder(new Manifest.Key(SETTINGS_MANIFEST, Long.toString(tag + 1)));
            Tree.Builder tree = new Tree.Builder();

            // apply IDs where required. this removes the requirement for the webapp to generate IDs on the server.
            if (config.auth != null && config.auth.ldapSettings != null && !config.auth.ldapSettings.isEmpty()) {
                for (var cfg : config.auth.ldapSettings) {
                    if (cfg.id == null) {
                        cfg.id = UuidHelper.randomId();
                    }
                }
            }

            tree.add(new Tree.Key(CONFIG_FILE, EntryType.BLOB), hive
                    .execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(encryptPasswords(hive, config, key)))));

            hive.execute(new InsertManifestOperation()
                    .addManifest(builder.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(tree))).build(hive)));

            hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(10).setToDelete(SETTINGS_MANIFEST));
        }
    }

    private static SettingsConfiguration decryptOrClearPasswords(SettingsConfiguration config, SecretKeySpec key,
            boolean clearPasswords) {
        List<LDAPSettingsDto> ldapSettings = config.auth.ldapSettings;
        MailSenderSettingsDto mailSenderSettings = config.mailSenderSettings;
        MailReceiverSettingsDto mailReceiverSettings = config.mailReceiverSettings;

        if (clearPasswords) {
            ldapSettings.stream().forEach(lds -> lds.pass = null);
            mailSenderSettings.password = null;
            mailReceiverSettings.password = null;
        } else {
            ldapSettings.stream().forEach(ldsDto -> {
                ldsDto.pass = decryptString(key, ldsDto.pass);
                if (StringHelper.isNullOrEmpty(ldsDto.pass)) {
                    log.warn("No password for {}", ldsDto.server);
                }
            });
            mailSenderSettings.password = decryptString(key, mailSenderSettings.password);
            mailReceiverSettings.password = decryptString(key, mailReceiverSettings.password);
        }

        return config;
    }

    private static String decryptString(SecretKeySpec key, String string) {
        if (!StringHelper.isNullOrEmpty(string)) {
            try {
                return SecurityHelper.decrypt(string, key);
            } catch (GeneralSecurityException e) {
                log.error("Cannot decrypt string", e);
            }
        }
        return string;
    }

    private static SettingsConfiguration encryptPasswords(BHive hive, SettingsConfiguration config, SecretKeySpec key) {
        SettingsConfiguration oldConfig = read(hive, key, false);

        List<LDAPSettingsDto> oldLdapSettingsList = oldConfig.auth.ldapSettings;
        for (LDAPSettingsDto newLdapSettings : config.auth.ldapSettings) {
            String newPLdapPassword = newLdapSettings.pass;

            if (StringHelper.isNullOrEmpty(newPLdapPassword)) {
                LDAPSettingsDto oldLdapSettings = oldLdapSettingsList.stream()//
                        .filter(l -> Objects.equals(l.id, newLdapSettings.id))//
                        .findAny().orElseThrow(() -> new IllegalStateException(
                                "No existing password found and no password supplied for LDAP server " + newLdapSettings.server));
                newLdapSettings.pass = tryEncryptString(key, oldLdapSettings.pass);
            } else {
                newLdapSettings.pass = tryEncryptString(key, newPLdapPassword);
            }
        }

        MailSenderSettingsDto mailSenderSettings = config.mailSenderSettings;
        mailSenderSettings.password =//
                encryptString(key, mailSenderSettings.password, oldConfig.mailReceiverSettings.password);

        MailReceiverSettingsDto mailReceiverSettings = config.mailReceiverSettings;
        mailReceiverSettings.password =//
                encryptString(key, mailReceiverSettings.password, oldConfig.mailReceiverSettings.password);

        return config;
    }

    private static String encryptString(SecretKeySpec key, String newString, String oldString) {
        if (StringHelper.isNullOrEmpty(newString)) {
            return oldString == null ? null : tryEncryptString(key, oldString);
        }
        return tryEncryptString(key, newString);
    }

    private static String tryEncryptString(SecretKeySpec key, String string) {
        try {
            return SecurityHelper.encrypt(string, key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt string", e);
        }
    }
}

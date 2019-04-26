package io.bdeploy.common.security;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Wraps around an actual (JCEKS) keystore on disc.
 *
 * @see SecurityHelper#loadPublicKeyStore(Path, char[])
 */
public class OnDiscKeyStore implements KeyStoreProvider {

    private final char[] pp;
    private final KeyStore ks;

    public OnDiscKeyStore(Path file, String passphrase) {
        this.pp = passphrase.toCharArray();
        try {
            this.ks = SecurityHelper.getInstance().loadPublicKeyStore(file, pp);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot load keystore from " + file, e);
        }
    }

    @Override
    public KeyStore getStore() {
        return ks;
    }

    @Override
    public char[] getPass() {
        return Arrays.copyOf(pp, pp.length);
    }

}

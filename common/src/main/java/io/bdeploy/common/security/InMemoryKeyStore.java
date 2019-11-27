package io.bdeploy.common.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Creates and caches in-memory {@link KeyStore}s for minion and hive remote
 * access.
 */
public class InMemoryKeyStore implements KeyStoreProvider {

    private final char[] pp;
    private KeyStore store;
    private final SecurityHelper helper = SecurityHelper.getInstance();
    private final String authPack;

    public InMemoryKeyStore(String authPack) {
        this(authPack, Long.toString(System.currentTimeMillis()).toCharArray());
    }

    private InMemoryKeyStore(String authPack, char[] pp) {
        this.authPack = authPack;
        this.pp = Arrays.copyOf(pp, pp.length);
    }

    /**
     * For testing only.
     */
    String getAuthPack() {
        return authPack;
    }

    @Override
    public KeyStore getStore() {
        if (store != null) {
            return store;
        }

        try {
            store = helper.loadPublicKeyStore((InputStream) null, pp);
            helper.importSignaturePack(authPack, store, pp);
            return store;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot prepare KeyStore", e);
        }
    }

    @Override
    public char[] getPass() {
        return Arrays.copyOf(pp, pp.length);
    }

}

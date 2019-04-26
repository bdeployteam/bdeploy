package io.bdeploy.common.security;

import java.security.KeyStore;

/**
 * Common interface for providers of {@link KeyStore}s.
 */
public interface KeyStoreProvider {

    public KeyStore getStore();

    public char[] getPass();

}

package io.bdeploy.common.security;

import io.bdeploy.common.security.InMemoryKeyStore;
import io.bdeploy.common.security.KeyStoreProvider;

public class AuthPackAccessor {

    public static String getAuthPack(KeyStoreProvider ks) {
        InMemoryKeyStore memoryKs = (InMemoryKeyStore) ks;
        return memoryKs.getAuthPack();
    }

}

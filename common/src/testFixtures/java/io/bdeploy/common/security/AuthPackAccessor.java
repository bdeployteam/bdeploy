package io.bdeploy.common.security;

public class AuthPackAccessor {

    public static String getAuthPack(KeyStoreProvider ks) {
        InMemoryKeyStore memoryKs = (InMemoryKeyStore) ks;
        return memoryKs.getAuthPack();
    }

}

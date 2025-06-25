package io.bdeploy.bhive.op;

import io.bdeploy.bhive.BHive;

/**
 * Invalidates caches on the manifest and object databases
 */
public class InvalidateCachesOperation extends BHive.Operation<Void> {

    @Override
    public Void call() {
        getManifestDatabase().invalidateCaches();
        getObjectManager().invalidateCaches();

        return null;
    }
}

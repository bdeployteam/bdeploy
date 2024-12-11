package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;

/**
 * Checks whether the given manifest exists in the underlying {@link BHive}
 */
@ReadOnlyOperation
public class ManifestExistsOperation extends BHive.Operation<Boolean> {

    private Manifest.Key manifest;

    @Override
    public Boolean call() {
        assertNotNull(manifest, "Manifest to check not set");
        return getManifestDatabase().hasManifest(manifest);
    }

    /**
     * The {@link Manifest} to check.
     */
    public ManifestExistsOperation setManifest(Manifest.Key key) {
        this.manifest = key;
        return this;
    }

}

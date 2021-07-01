package io.bdeploy.bhive.op;

import java.util.Set;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;

/**
 * Lists all {@link Manifest}s available in the {@link BHive}.
 */
@ReadOnlyOperation
public class ManifestListOperation extends BHive.Operation<Set<Manifest.Key>> {

    private String key;

    @Override
    public Set<Manifest.Key> call() throws Exception {
        if (key == null) {
            return getManifestDatabase().getAllManifests();
        } else {
            return getManifestDatabase().getAllForName(key);
        }
    }

    /**
     * @param name the name of the manifest to calculate a new version for. Note that this can be any number of full segments of
     *            the name. Segments are separated with '/'.
     * @return the operation for chaining.
     */
    public ManifestListOperation setManifestName(String name) {
        this.key = name;
        return this;
    }

}

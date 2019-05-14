package io.bdeploy.bhive.op;

import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Lists all {@link Manifest}s available in the {@link BHive}.
 */
public class ManifestListOperation extends BHive.Operation<SortedSet<Manifest.Key>> {

    private String key;

    @Override
    public SortedSet<Manifest.Key> call() throws Exception {
        try (Activity activity = getActivityReporter().start("Listing manifests...", -1)) {
            if (key == null) {
                return getManifestDatabase().getAllManifests();
            } else {
                return getManifestDatabase().getAllForName(key);
            }
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

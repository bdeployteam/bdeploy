package io.bdeploy.bhive.op;

import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Returns the highest currently available version number for Manifests which use a simple counter versioning scheme.
 */
@ReadOnlyOperation
public class ManifestMaxIdOperation extends BHive.Operation<Optional<Long>> {

    private String key;

    @Override
    public Optional<Long> call() throws Exception {
        RuntimeAssert.assertNotNull(key, "No Manifest to inspect");

        try {
            return getManifestDatabase().getAllForName(key).stream().map(Manifest.Key::getTag).map(Long::parseLong)
                    .max(Long::compare);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * @param name the name of the manifest to calculate a new version for. Note that this must be the <b>full</b> name of the
     *            manifest, not just a segment.
     * @return the operation for chaining.
     */
    public ManifestMaxIdOperation setManifestName(String name) {
        this.key = name;
        return this;
    }

}

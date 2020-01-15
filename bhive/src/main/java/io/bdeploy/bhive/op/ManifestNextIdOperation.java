package io.bdeploy.bhive.op;

import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Returns the next available version number for Manifests which use a simple counter versioning scheme.
 */
@ReadOnlyOperation
public class ManifestNextIdOperation extends BHive.Operation<Long> {

    private String key;

    @Override
    public Long call() throws Exception {
        RuntimeAssert.assertNotNull(key, "No Manifest to inspect");

        try (Activity activity = getActivityReporter().start("Evaluating next manifest version...", -1)) {
            Optional<Long> max = execute(new ManifestMaxIdOperation().setManifestName(key));
            return max.orElse(0l) + 1;
        }
    }

    /**
     * @param name the name of the manifest to calculate a new version for. Note that this must be the <b>full</b> name of the
     *            manifest, not just a segment.
     * @return the operation for chaining.
     */
    public ManifestNextIdOperation setManifestName(String name) {
        this.key = name;
        return this;
    }

}

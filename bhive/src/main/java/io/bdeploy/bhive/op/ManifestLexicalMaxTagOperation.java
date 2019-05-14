package io.bdeploy.bhive.op;

import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Returns the lexically "newest" tag for a given manifest.
 */
public class ManifestLexicalMaxTagOperation extends BHive.Operation<Optional<String>> {

    private String key;

    @Override
    public Optional<String> call() throws Exception {
        RuntimeAssert.assertNotNull(key, "No Manifest to inspect");

        try (Activity activity = getActivityReporter().start("Evaluating latest manifest version...", -1)) {
            Optional<String> max = getManifestDatabase().getAllForName(key).stream().map(Manifest.Key::getTag)
                    .sorted((a, b) -> b.compareTo(a)).findFirst();
            return max;
        }
    }

    /**
     * @param name the name of the manifest to calculate a new version for. Note that this must be the <b>full</b> name of the
     *            manifest, not just a segment.
     * @return the operation for chaining.
     */
    public ManifestLexicalMaxTagOperation setManifestName(String name) {
        this.key = name;
        return this;
    }

}

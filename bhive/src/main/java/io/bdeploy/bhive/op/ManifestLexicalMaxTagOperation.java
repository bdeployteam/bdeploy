package io.bdeploy.bhive.op;

import java.util.Optional;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Returns the lexically "newest" tag for a given manifest.
 */
@ReadOnlyOperation
public class ManifestLexicalMaxTagOperation extends BHive.Operation<Optional<String>> {

    private String key;

    @Override
    public Optional<String> call() {
        RuntimeAssert.assertNotNull(key, "No Manifest to inspect");

        return getManifestDatabase().getAllForName(key).stream().map(Manifest.Key::getTag).sorted((a, b) -> b.compareTo(a))
                .findFirst();
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

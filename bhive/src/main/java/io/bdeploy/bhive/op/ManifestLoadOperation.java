package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Loads the specified {@link Manifest} from its underlying storage in the
 * {@link ManifestDatabase} of the {@link BHive}.
 */
@ReadOnlyOperation
public class ManifestLoadOperation extends BHive.Operation<Manifest> {

    private Manifest.Key manifest;

    @Override
    public Manifest call() throws Exception {
        assertNotNull(manifest, "Manifest to load not set");

        try (Activity activity = getActivityReporter().start("Loading manifest...", -1)) {
            return getManifestDatabase().getManifest(manifest);
        }
    }

    /**
     * The {@link Manifest} to load.
     */
    public ManifestLoadOperation setManifest(Manifest.Key key) {
        this.manifest = key;
        return this;
    }

}

package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;

/**
 * Operation to delete a single manifest from the {@link ManifestDatabase} of
 * the {@link BHive}.
 * <p>
 * Note that underlying recursively required objects are NOT deleted from the
 * {@link ObjectDatabase}. See {@link PruneOperation}.
 */
public class ManifestDeleteOperation extends BHive.Operation<Manifest.Key> {

    private Manifest.Key toDelete;

    @Override
    public Manifest.Key call() throws Exception {
        assertNotNull(toDelete, "Manifest to delete not set");

        getManifestDatabase().removeManifest(toDelete);

        return toDelete;
    }

    /**
     * Set the {@link Manifest} to be deleted.
     */
    public ManifestDeleteOperation setToDelete(Manifest.Key toDelete) {
        this.toDelete = toDelete;
        return this;
    }

}

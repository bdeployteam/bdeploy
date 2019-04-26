package io.bdeploy.bhive.op;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectDatabase;

/**
 * Create a {@link Manifest} reference in the {@link ObjectDatabase} and returns
 * its {@link ObjectId}.
 */
public class InsertManifestRefOperation extends BHive.Operation<ObjectId> {

    private Manifest.Key manifest;

    @Override
    public ObjectId call() throws Exception {
        return getObjectManager().insertManifestReference(manifest);
    }

    /**
     * Set the {@link Manifest} to reference.
     */
    public InsertManifestRefOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

}

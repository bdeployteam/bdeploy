package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;
import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ReferenceHandler;

/**
 * Export a {@link Manifest}s root {@link Tree} to a target directory.
 */
public class ExportOperation extends BHive.Operation<Manifest.Key> {

    private Manifest.Key manifest;
    private Path target;
    private ReferenceHandler refHandler;

    @Override
    public Manifest.Key call() throws Exception {
        assertNotNull(manifest, "Manifest not set");
        assertNotNull(target, "Target path not set");

        SortedSet<Manifest.Key> keys = getManifestDatabase().getAllForName(manifest.getName());
        if (!keys.contains(manifest)) {
            throw new IllegalArgumentException("Manifest not found: " + manifest);
        }

        Manifest mf = getManifestDatabase().getManifest(manifest);
        getObjectManager().exportTree(mf.getRoot(), target, refHandler);

        return manifest;
    }

    /**
     * Set the manifest to export.
     */
    public ExportOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Set the target path to export into.
     */
    public ExportOperation setTarget(Path target) {
        this.target = target;
        return this;
    }

    /**
     * Set a custom reference handler which takes care of nested (recursive) manifest references.
     */
    public ExportOperation setReferenceHandler(ReferenceHandler handler) {
        this.refHandler = handler;
        return this;
    }

}

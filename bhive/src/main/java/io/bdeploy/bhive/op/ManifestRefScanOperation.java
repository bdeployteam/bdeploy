package io.bdeploy.bhive.op;

import java.util.SortedMap;
import java.util.TreeMap;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.MissingObjectView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Scans for nested {@link Manifest}s referenced in the given {@link Manifest}.
 */
public class ManifestRefScanOperation extends BHive.Operation<SortedMap<String, Manifest.Key>> {

    private Manifest.Key manifest;
    private boolean allowMissing = false;
    private int maxDepth = Integer.MAX_VALUE;

    @Override
    public SortedMap<String, Manifest.Key> call() throws Exception {
        RuntimeAssert.assertNotNull(manifest, "Nothing to scan");

        Manifest mf = execute(new ManifestLoadOperation().setManifest(manifest));
        SortedMap<String, Manifest.Key> cachedReferences = mf.getCachedReferences(this, maxDepth, allowMissing);
        if (cachedReferences != null) {
            return cachedReferences;
        }

        SortedMap<String, Manifest.Key> referenced = new TreeMap<>();
        try (Activity activity = getActivityReporter().start("Scanning for manifests references...", -1)) {
            ObjectId root = mf.getRoot();
            if (allowMissing && !execute(new ObjectExistsOperation().addObject(root)).contains(root)) {
                // root tree is not here, but this is OK if copying from a partial hive
                return referenced;
            }

            TreeView state = execute(new ScanOperation().setManifest(manifest).setMaxDepth(maxDepth));
            state.visit(new TreeVisitor.Builder().onMissing(this::missing)
                    .onManifestRef(m -> referenced.put(m.getPathString(), m.getReferenced())).build());

            return referenced;
        }
    }

    private void missing(MissingObjectView m) {
        if (!allowMissing) {
            throw new IllegalStateException("Missing object: " + m.getElementId() + " at " + m.getPath());
        }
    }

    public ManifestRefScanOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

    public ManifestRefScanOperation setAllowMissingObjects(boolean allow) {
        this.allowMissing = allow;
        return this;
    }

    public ManifestRefScanOperation setMaxDepth(int max) {
        this.maxDepth = max;
        return this;
    }

}

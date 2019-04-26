package io.bdeploy.bhive.op;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Scan a given {@link Manifest} and return a {@link TreeView} of it's content.
 */
public class ScanOperation extends BHive.Operation<TreeView> {

    private ObjectId treeId;
    private int maxDepth = Integer.MAX_VALUE;
    private Manifest.Key manifest;

    @Override
    public TreeView call() throws Exception {
        if (manifest != null) {
            Manifest mf = execute(new ManifestLoadOperation().setManifest(manifest));
            RuntimeAssert.assertNotNull(mf, "Given manifest not found");
            treeId = mf.getRoot();
        }

        RuntimeAssert.assertNotNull(treeId, "No tree to scan");

        return getObjectManager().scan(treeId, maxDepth);
    }

    /**
     * Set the {@link Manifest} to scan. Scans the {@link Manifest}s root tree.
     */
    public ScanOperation setManifest(Manifest.Key manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Set the {@link Tree} to scan.
     */
    public ScanOperation setTree(ObjectId treeId) {
        this.treeId = treeId;
        return this;
    }

    /**
     * Limit the depth of the scan.
     */
    public ScanOperation setMaxDepth(int max) {
        this.maxDepth = max;
        return this;
    }

}

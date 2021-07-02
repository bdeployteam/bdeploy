package io.bdeploy.bhive.op;

import java.util.Collections;
import java.util.Map.Entry;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Scan a given {@link Manifest} and return a {@link TreeView} of it's content.
 */
@ReadOnlyOperation
public class ScanOperation extends BHive.Operation<TreeView> {

    private ObjectId treeId;
    private int maxDepth = Integer.MAX_VALUE;
    private Manifest.Key manifest;
    private boolean followReferences = true;

    @Override
    public TreeView call() throws Exception {
        try (Activity activity = getActivityReporter().start("Scanning", -1)) {
            if (manifest != null) {
                Manifest mf = execute(new ManifestLoadOperation().setManifest(manifest));
                RuntimeAssert.assertNotNull(mf, "Given manifest not found");
                treeId = mf.getRoot();
            }

            RuntimeAssert.assertNotNull(treeId, "No tree to scan");

            TreeView result = getObjectManager().scan(treeId, maxDepth, followReferences);
            if (result.getChildren().size() == 1) {
                Entry<String, ElementView> entry = result.getChildren().entrySet().iterator().next();

                if (manifest != null && entry.getValue() instanceof DamagedObjectView
                        && entry.getValue().getElementId().equals(treeId)) {
                    // the root tree is damaged, this is... bad. for better visibility put the manifest ID in there.
                    result = new TreeView(result.getElementId(), result.getPath());
                    result.addChild(
                            new DamagedObjectView(treeId, EntryType.MANIFEST, Collections.singleton(manifest.toString())));
                }
            }

            return result;
        }
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

    /**
     * Set whether to follow {@link Manifest} references. Defaults to <code>true</code>.
     */
    public ScanOperation setFollowReferences(boolean follow) {
        this.followReferences = follow;
        return this;
    }

}

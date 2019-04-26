package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.InputStream;

import com.google.common.base.Splitter;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;

/**
 * Loads the specified file by relative path from a {@link Tree} and its
 * underlying storage in the {@link ObjectDatabase} of the {@link BHive}.
 */
public class TreeEntryLoadOperation extends BHive.Operation<InputStream> {

    private String relPath;
    private ObjectId tree;

    @Override
    public InputStream call() throws Exception {
        assertNotNull(relPath, "File to load not set");
        assertNotNull(tree, "Tree to load from not set");

        return getObjectManager().getStreamForRelativePath(tree, Splitter.on('/').splitToList(relPath).toArray(new String[0]));
    }

    /**
     * The {@link Tree}s {@link ObjectId} to load from.
     */
    public TreeEntryLoadOperation setRootTree(ObjectId tree) {
        this.tree = tree;
        return this;
    }

    /**
     * Set the relative path of the file to load.
     */
    public TreeEntryLoadOperation setRelativePath(String relPath) {
        this.relPath = relPath;
        return this;
    }

}

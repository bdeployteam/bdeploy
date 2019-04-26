package io.bdeploy.bhive.objects.view.scanner;

import java.util.function.Consumer;
import java.util.function.Function;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.DamagedObjectView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.MissingObjectView;
import io.bdeploy.bhive.objects.view.SkippedElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.op.ObjectConsistencyCheckOperation;

/**
 * Container for functional visitors on a {@link TreeView}.
 */
public class TreeVisitor {

    private Consumer<BlobView> blobConsumer;
    private Consumer<DamagedObjectView> damagedConsumer;
    private Consumer<ManifestRefView> manifestConsumer;
    private Consumer<MissingObjectView> missingConsumer;
    private Consumer<SkippedElementView> skippedConsumer;
    private Function<TreeView, Boolean> treeConsumer;

    private TreeVisitor() {
    }

    /**
     * @param snapshot the snapshot to process.
     * @return whether to continue with a {@link TreeView}s children if the snapshot is a {@link TreeView}.
     */
    public boolean accept(ElementView snapshot) {
        boolean visitChildren = true;
        if (snapshot instanceof DamagedObjectView && damagedConsumer != null) {
            damagedConsumer.accept((DamagedObjectView) snapshot);
        }
        if (snapshot instanceof MissingObjectView && missingConsumer != null) {
            missingConsumer.accept((MissingObjectView) snapshot);
        }
        if (snapshot instanceof BlobView && blobConsumer != null) {
            blobConsumer.accept((BlobView) snapshot);
        }
        if (snapshot instanceof ManifestRefView && manifestConsumer != null) {
            manifestConsumer.accept((ManifestRefView) snapshot);
            // manifest reference always also holds a tree reference
        }
        if (snapshot instanceof SkippedElementView && skippedConsumer != null) {
            skippedConsumer.accept((SkippedElementView) snapshot);
        }
        if (snapshot instanceof TreeView && treeConsumer != null) {
            visitChildren = treeConsumer.apply((TreeView) snapshot);
        }
        return visitChildren;
    }

    /**
     * Build a new {@link TreeVisitor} suitable for {@link TreeView#visit(TreeVisitor)}.
     */
    public static class Builder {

        private final TreeVisitor result = new TreeVisitor();

        /**
         * Register a {@link Consumer} triggered when a entry of type {@link EntryType#BLOB} is visited.
         */
        public Builder onBlob(Consumer<BlobView> blob) {
            result.blobConsumer = blob;
            return this;
        }

        /**
         * Register a {@link Consumer} triggered when an entry is 'damaged'.
         * <p>
         * An entry is damaged if it is an internal tree management object ({@link Tree} or {@link Manifest} reference) and cannot
         * be de-serialized, or if it's content checksum does not match the expected checksum (only validated during
         * {@link ObjectConsistencyCheckOperation}).
         * <p>
         * Note that a damaged object will also trigger the {@link #onMissing(Consumer)} as the damaged object is not available.
         */
        public Builder onDamaged(Consumer<DamagedObjectView> damaged) {
            result.damagedConsumer = damaged;
            return this;
        }

        /**
         * Register a {@link Consumer} triggered when an entry is a {@link Manifest} reference within a {@link Tree}.
         * <p>
         * Note that a {@link Manifest} reference will also trigger the {@link #onTree(Function)} {@link Function} as a
         * {@link Manifest} reference basically 'inserts' the {@link Manifest} root tree at that location in the source
         * {@link Tree}.
         */
        public Builder onManifestRef(Consumer<ManifestRefView> mf) {
            result.manifestConsumer = mf;
            return this;
        }

        /**
         * Register a {@link Consumer} triggered when a missing/unavailable object is found during {@link Tree} traversal.
         */
        public Builder onMissing(Consumer<MissingObjectView> missing) {
            result.missingConsumer = missing;
            return this;
        }

        /**
         * Register a {@link Consumer} triggered when an object is skipped. This happens when the scanners maximum depth has been
         * reached.
         */
        public Builder onSkipped(Consumer<SkippedElementView> skipped) {
            result.skippedConsumer = skipped;
            return this;
        }

        /**
         * @param tree the tree element {@link Function}. The function must return a {@link Boolean} determining whether children
         *            of this {@link TreeView} should be visited.
         * @return this for chaining.
         */
        public Builder onTree(Function<TreeView, Boolean> tree) {
            result.treeConsumer = tree;
            return this;
        }

        /**
         * @return the {@link TreeVisitor} with all registered triggers, suitable for {@link TreeView#visit(TreeVisitor)}.
         */
        public TreeVisitor build() {
            return result;
        }
    }

}

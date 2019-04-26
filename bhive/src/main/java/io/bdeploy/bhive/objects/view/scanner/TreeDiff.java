package io.bdeploy.bhive.objects.view.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;

/**
 * Used to calculate the differences of two {@link TreeView}s.
 */
public class TreeDiff {

    private final TreeView left;
    private final TreeView right;

    public TreeDiff(TreeView left, TreeView right) {
        this.left = left;
        this.right = right;
    }

    /**
     * @return the actual differences of the two {@link TreeView}s.
     */
    public List<TreeElementDiff> diff() {
        FlattenedTree flatLeft = new FlattenedTree(left);
        FlattenedTree flatRight = new FlattenedTree(right);

        Map<String, FlattenedTreeEntry> itemsLeft = flatLeft.flatten();
        Map<String, FlattenedTreeEntry> itemsRight = flatRight.flatten();

        Set<String> keysOnlyLeft = new TreeSet<>(itemsLeft.keySet());
        Set<String> keysOnlyRight = new TreeSet<>(itemsRight.keySet());

        keysOnlyLeft.removeAll(itemsRight.keySet());
        keysOnlyRight.removeAll(itemsLeft.keySet());

        List<TreeElementDiff> diffs = new ArrayList<>();

        keysOnlyLeft.forEach(k -> diffs.add(TreeElementDiff.onlyLeft(itemsLeft.get(k).element, itemsLeft.get(k).type)));
        keysOnlyRight.forEach(k -> diffs.add(TreeElementDiff.onlyRight(itemsRight.get(k).element, itemsRight.get(k).type)));

        Set<String> keysOnBoth = new TreeSet<>(itemsLeft.keySet());
        keysOnBoth.retainAll(itemsRight.keySet());

        for (String k : keysOnBoth) {
            FlattenedTreeEntry entryLeft = itemsLeft.get(k);
            FlattenedTreeEntry entryRight = itemsRight.get(k);

            ObjectId leftMfRef = null;
            ObjectId rightMfRef = null;
            if (entryLeft.element instanceof ManifestRefView) {
                leftMfRef = ((ManifestRefView) entryLeft.element).getReferenceId();
            }
            if (entryRight.element instanceof ManifestRefView) {
                rightMfRef = ((ManifestRefView) entryRight.element).getReferenceId();
            }
            if (Objects.equals(leftMfRef, rightMfRef)
                    && entryLeft.element.getElementId().equals(entryRight.element.getElementId())) {
                continue;
            }

            diffs.add(TreeElementDiff.content(entryLeft.element, entryRight.element, entryLeft.type, entryRight.type));
        }

        return diffs;
    }

    /**
     * A flat representation of a {@link TreeView}. Paths within a {@link TreeView} are guaranteed to be unique, this this
     * {@link FlattenedTree} can safely use them as key.
     */
    private static class FlattenedTree {

        private final Map<String, FlattenedTreeEntry> result = new TreeMap<>();
        private final TreeView snapshot;

        public FlattenedTree(TreeView snapshot) {
            this.snapshot = snapshot;
        }

        /**
         * Recursively visits the {@link TreeView} and indexes all elements to a flat {@link Map}.
         */
        synchronized Map<String, FlattenedTreeEntry> flatten() {
            if (!result.isEmpty()) {
                return result;
            }

            snapshot.visit(new TreeVisitor.Builder().onMissing(this::invalid).onSkipped(this::invalid).onBlob(this::blob)
                    .onManifestRef(this::manifest).onTree(this::tree).build());

            return result;
        }

        private void add(ElementView s, Tree.EntryType t) {
            result.put(s.getPathString(), new FlattenedTreeEntry(s, t));
        }

        private void blob(BlobView b) {
            add(b, EntryType.BLOB);
        }

        private void manifest(ManifestRefView m) {
            add(m, EntryType.MANIFEST);
        }

        private Boolean tree(TreeView t) {
            if (t instanceof ManifestRefView) {
                return true; // handled by #manifest(ManifestRefSnapshot)
            }

            add(t, EntryType.TREE);
            return true;
        }

        private void invalid(ElementView sn) {
            throw new IllegalStateException(
                    "Diff on damaged trees not supported, missing " + sn.getPath() + " [" + sn.getElementId() + "]");
        }

    }

    private static class FlattenedTreeEntry {

        ElementView element;
        Tree.EntryType type;

        FlattenedTreeEntry(ElementView element, Tree.EntryType type) {
            this.element = element;
            this.type = type;
        }

    }

}

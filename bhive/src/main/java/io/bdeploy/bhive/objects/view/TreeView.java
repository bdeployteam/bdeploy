package io.bdeploy.bhive.objects.view;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;

/**
 * A recursive view of a {@link Tree}'s metadata, usually a {@link Manifest}'s root {@link Tree}.
 * <p>
 * The referenced recursive elements hold relevant information of {@link Tree} entries (blobs, trees, and as {@link Manifest}
 * references). It does, however, not hold any expensive-to-calculate information (file sizes, attributes, etc.).
 */
public class TreeView extends ElementView {

    private final Map<String, ElementView> children = new TreeMap<>();

    public TreeView(ObjectId treeId, Collection<String> path) {
        super(treeId, path);
    }

    /**
     * @param snapshot the snapshot to add to the tree.
     */
    public void addChild(ElementView snapshot) {
        children.put(snapshot.getName(), snapshot);
    }

    /**
     * @return all children of this {@link TreeView}.
     */
    public Map<String, ElementView> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    /**
     * Visit all children of this tree recursively until the scanner opts out.
     *
     * @param scanner the scanner to call for each element.
     */
    public void visit(TreeVisitor scanner) {
        if (scanner.accept(this)) {
            for (Entry<String, ElementView> entry : children.entrySet()) {
                ElementView element = entry.getValue();

                if (element instanceof TreeView) {
                    ((TreeView) element).visit(scanner);
                } else {
                    scanner.accept(element);
                }
            }
        }
    }

    /**
     * Visit all children of this tree recursively in a DFS manner.
     *
     * @param scanner the scanner to call for each element.
     */
    public void visitDfs(TreeVisitor scanner) {
        for (Entry<String, ElementView> entry : children.entrySet()) {
            ElementView element = entry.getValue();
            if (element instanceof TreeView) {
                ((TreeView) element).visitDfs(scanner);
            } else {
                scanner.accept(element);
            }
        }
        scanner.accept(this);
    }

}

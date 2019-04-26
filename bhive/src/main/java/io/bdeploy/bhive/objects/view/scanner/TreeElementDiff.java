package io.bdeploy.bhive.objects.view.scanner;

import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.TreeView;

/**
 * Represents a single difference when comparing two {@link TreeView}s.
 *
 * @see TreeDiff
 */
public class TreeElementDiff {

    public enum DifferenceType {
        ONLY_LEFT,
        ONLY_RIGHT,
        CONTENT_DIFF
    }

    private final ElementView left;
    private final ElementView right;
    private final DifferenceType type;
    private final EntryType leftType;
    private final EntryType rightType;

    private TreeElementDiff(ElementView left, ElementView right, DifferenceType type, Tree.EntryType leftType,
            Tree.EntryType rightType) {
        this.left = left;
        this.right = right;
        this.type = type;
        this.leftType = leftType;
        this.rightType = rightType;
    }

    /**
     * @param element the {@link ElementView} only present in the 'left' {@link TreeView}
     * @param type the advertised {@link EntryType} in the source {@link Tree}.
     * @return the {@link TreeElementDiff} corresponding to the {@link ElementView}.
     */
    public static TreeElementDiff onlyLeft(ElementView element, Tree.EntryType type) {
        return new TreeElementDiff(element, null, DifferenceType.ONLY_LEFT, type, null);
    }

    /**
     * @param element the {@link ElementView} only present in the 'right' {@link TreeView}
     * @param type the advertised {@link EntryType} in the source {@link Tree}.
     * @return the {@link TreeElementDiff} corresponding to the {@link ElementView}.
     */
    public static TreeElementDiff onlyRight(ElementView element, Tree.EntryType type) {
        return new TreeElementDiff(null, element, DifferenceType.ONLY_RIGHT, null, type);
    }

    /**
     * @param left the {@link ElementView} present in the 'left' {@link TreeView}
     * @param right the {@link ElementView} present in the 'right' {@link TreeView}
     * @param leftType the advertised {@link EntryType} of the 'left' {@link ElementView} the source {@link Tree}.
     * @param rightType the advertised {@link EntryType} of the 'left' {@link ElementView} the source {@link Tree}.
     * @return the {@link TreeElementDiff} corresponding to the {@link ElementView}s present in both {@link TreeView}s.
     */
    public static TreeElementDiff content(ElementView left, ElementView right, Tree.EntryType leftType,
            Tree.EntryType rightType) {
        return new TreeElementDiff(left, right, DifferenceType.CONTENT_DIFF, leftType, rightType);
    }

    /**
     * @return the 'left' {@link ElementView}. Not set if {@link #getType() type} is {@link DifferenceType#ONLY_RIGHT}.
     */
    public ElementView getLeft() {
        return left;
    }

    /**
     * @return the type of the 'left' {@link ElementView}. Not set if {@link #getType() type} is
     *         {@link DifferenceType#ONLY_RIGHT}.
     */
    public EntryType getLeftType() {
        return leftType;
    }

    /**
     * @return the 'right' {@link ElementView}. Not set if {@link #getType() type} is {@link DifferenceType#ONLY_LEFT}.
     */
    public ElementView getRight() {
        return right;
    }

    /**
     * @return the type of the 'right' {@link ElementView}. Not set if {@link #getType() type} is
     *         {@link DifferenceType#ONLY_LEFT}.
     */
    public EntryType getRightType() {
        return rightType;
    }

    /**
     * @return the type of the difference.
     */
    public DifferenceType getType() {
        return type;
    }

    @Override
    public String toString() {
        switch (type) {
            case CONTENT_DIFF:
                return type.name() + ": " + left.getPath() + ": " + format(left, leftType) + " <--> " + format(right, rightType);
            case ONLY_LEFT:
                return type.name() + ": " + left.getPath() + ": " + format(left, leftType);
            case ONLY_RIGHT:
                return type.name() + ": " + right.getPath() + ": " + format(right, rightType);
            default:
                return "<Unknown Difference>";
        }
    }

    private String format(ElementView s, Tree.EntryType t) {
        return "[" + t.name() + "]" + s.getElementId();
    }

}
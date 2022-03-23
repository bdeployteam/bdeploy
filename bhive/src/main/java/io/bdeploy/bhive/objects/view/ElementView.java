package io.bdeploy.bhive.objects.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Generated;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ScanOperation;

/**
 * Base of all view carried by a {@link TreeView} recursively.
 */
public abstract class ElementView implements Comparable<ElementView> {

    private final ObjectId id;
    private final List<String> path;

    protected ElementView(ObjectId id, Collection<String> path) {
        this.id = id;
        this.path = new ArrayList<>(path);
    }

    /**
     * @return the {@link ObjectId} of the referenced element.
     */
    public ObjectId getElementId() {
        return id;
    }

    /**
     * @return the path relative to the {@link Tree} which was scanned.
     * @see ScanOperation
     */
    public List<String> getPath() {
        return Collections.unmodifiableList(path);
    }

    /**
     * @return {@link #getPath()} as human readable relative {@link String}.
     */
    public String getPathString() {
        return String.join("/", path);
    }

    /**
     * @return the element name (file or directory name) denoted by the element path
     * @see #getPath()
     */
    public String getName() {
        if (path.isEmpty()) {
            return "ROOT";
        }
        return path.get(path.size() - 1);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[path:" + getPathString() + ", id:" + id + "]";
    }

    @Override
    public int compareTo(ElementView o) {
        return id.compareTo(o.id);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ElementView other = (ElementView) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

}

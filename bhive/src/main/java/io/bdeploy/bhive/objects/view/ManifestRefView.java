package io.bdeploy.bhive.objects.view;

import java.util.Collection;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;

/**
 * View of a {@link Manifest} reference in a {@link Tree}.
 * <p>
 * A {@link Manifest} reference uses the referenced {@link Manifest}'s root {@link Tree} {@link ObjectId} as
 * {@link #getElementId() element ID}. Since {@link ManifestRefView} extends {@link TreeView} this means that
 * {@link ManifestRefView}'s can be visited as {@link TreeView}'s without caring that this is actually a referenced
 * {@link Manifest}.
 * <p>
 * The {@link ObjectId} of the actual reference in the source {@link Tree} can be obtained using {@link #getReferenceId()} in case
 * it is required (i.e. collecting all {@link ObjectId}s transitively required by a {@link Tree}).
 */
public class ManifestRefView extends TreeView {

    private final Key referenced;
    private final ObjectId refId;

    public ManifestRefView(ObjectId refId, Manifest.Key referenced, ObjectId rootTreeId, Collection<String> path) {
        super(rootTreeId, path);

        this.referenced = referenced;
        this.refId = refId;
    }

    /**
     * @return the {@link Key} of the referenced {@link Manifest}.
     */
    public Manifest.Key getReferenced() {
        return referenced;
    }

    /**
     * @return the {@link ObjectId} of the actual reference in the source {@link Tree}.
     */
    public ObjectId getReferenceId() {
        return refId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[path:" + getPathString() + ", id:" + getElementId() + ", ref: " + referenced + " {"
                + refId + "}]";
    }

}

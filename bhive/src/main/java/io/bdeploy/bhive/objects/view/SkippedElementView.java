package io.bdeploy.bhive.objects.view;

import java.util.Collection;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ScanOperation;

/**
 * Holds information about an object which was skipped during a {@link Tree} scan.
 * <p>
 * This usually happens when limiting the maximum scan depth. Omitted objects are (non-recursive) represented by
 * {@link SkippedElementView}s.
 *
 * @see ScanOperation
 */
public class SkippedElementView extends ElementView {

    public SkippedElementView(ObjectId id, Collection<String> path) {
        super(id, path);
    }

}

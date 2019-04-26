package io.bdeploy.bhive.objects.view;

import java.util.Collection;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;

/**
 * View of a blob (simple file content stored in a {@link BHive}).
 */
public class BlobView extends ElementView {

    public BlobView(ObjectId id, Collection<String> path) {
        super(id, path);
    }

}

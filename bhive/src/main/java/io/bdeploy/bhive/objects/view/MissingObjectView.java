package io.bdeploy.bhive.objects.view;

import java.util.Collection;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.model.Tree.Key;

/**
 * Refers to a missing object. This means that the object could not be found in the database, but was referenced by a
 * {@link Tree}.
 */
public class MissingObjectView extends ElementView {

    private final EntryType type;

    public MissingObjectView(ObjectId id, EntryType type, Collection<String> path) {
        super(id, path);

        this.type = type;
    }

    /**
     * @return the expected {@link Key} {@link EntryType}.
     */
    public EntryType getType() {
        return type;
    }

}

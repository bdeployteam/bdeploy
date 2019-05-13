package io.bdeploy.bhive.objects;

import java.nio.file.Path;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree.Key;

/**
 * Default reference handler which recursively exports {@link Manifest} references inline in the referencing location.
 */
public class DefaultReferenceHandler implements ReferenceHandler {

    private final ObjectManager om;

    public DefaultReferenceHandler(ObjectManager om) {
        this.om = om;
    }

    @Override
    public void onReference(Path location, Key key, Manifest referenced) {
        om.exportTree(referenced.getRoot(), location.resolve(key.getName()), this);
    }

}
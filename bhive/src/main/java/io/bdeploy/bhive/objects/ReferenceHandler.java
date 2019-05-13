package io.bdeploy.bhive.objects;

import java.nio.file.Path;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.Key;

/**
 * A handler which acts upon {@link Manifest} references in a {@link Tree}.
 */
public interface ReferenceHandler {

    /**
     * @param location the location where the reference is located (the references <b>parent</b> directory).
     * @param key the {@link Key} referencing the manifest.
     * @param referenced the referenced manifest
     */
    public void onReference(Path location, Tree.Key key, Manifest referenced);
}
package io.bdeploy.bhive;

import java.util.Collection;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;

/**
 * Listener which is notified when a {@link Manifest} is created from remote
 */
public interface ManifestSpawnListener {

    /**
     * Notification that new {@link Manifest} has spawned.
     */
    public void spawn(Collection<Manifest.Key> keys);
}
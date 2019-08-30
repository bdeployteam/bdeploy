package io.bdeploy.ui.api.impl;

import io.bdeploy.bhive.model.Manifest;

/**
 * Singleton wrapper around lazily (on request) initialized {@link InstanceEventBroadcaster}.
 */
public class InstanceEventManager {

    private InstanceEventBroadcaster bc;

    public void create(String instanceId, Manifest.Key key) {
        if (bc != null) {
            bc.create(instanceId, key);
        }
    }

    public void stateChanged(String instanceId, Manifest.Key key) {
        if (bc != null) {
            bc.stateChanged(instanceId, key);
        }
    }

    void register(InstanceEventBroadcaster bc) {
        this.bc = bc;
    }

}

package io.bdeploy.bhive.op;

import io.bdeploy.bhive.BHive;
import io.bdeploy.common.util.PathHelper;

/**
 * Clears all markers created using the given UUID. The UUID is obtained using {@link CreateObjectMarkersOperation}.
 */
public class ClearObjectMarkersOperation extends BHive.Operation<Void> {

    private String markerUuid;

    @Override
    public Void call() throws Exception {
        if (markerUuid == null) {
            // means nothing to clear as no markers where created.
            return null;
        }

        PathHelper.deleteRecursive(getMarkerRoot().resolve(markerUuid));
        return null;
    }

    public ClearObjectMarkersOperation setMarkersUuuid(String uuid) {
        this.markerUuid = uuid;
        return this;
    }

}

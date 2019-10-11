package io.bdeploy.bhive.op;

import java.util.SortedSet;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.UuidHelper;

/**
 * Creates a new {@link MarkerDatabase} in the target {@link BHive} and fills it with the provided IDs
 */
public class CreateObjectMarkersOperation extends BHive.Operation<String> {

    private SortedSet<ObjectId> ids;

    @Override
    public String call() throws Exception {
        String markerUuid = UuidHelper.randomId();
        RuntimeAssert.assertNotNull(ids, "no ids to protect set");

        if (ids.isEmpty()) {
            return null;
        }

        MarkerDatabase.waitRootLock(getMarkerRoot());
        MarkerDatabase mdb = new MarkerDatabase(getMarkerRoot().resolve(markerUuid), getActivityReporter());
        ids.forEach(mdb::addMarker);

        return markerUuid;
    }

    public CreateObjectMarkersOperation setObjectIds(SortedSet<ObjectId> objects) {
        this.ids = objects;
        return this;
    }

}

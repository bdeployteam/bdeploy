package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.InputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Loads the specified {@link ObjectId} from its underlying storage in the
 * {@link ObjectDatabase} of the {@link BHive}.
 */
public class ObjectLoadOperation extends BHive.Operation<InputStream> {

    private ObjectId objectId;

    @Override
    public InputStream call() throws Exception {
        assertNotNull(objectId, "Object to load not set");
        try (Activity activity = getActivityReporter().start("Retrieving object stream...", -1)) {
            return getObjectManager().db(x -> x.getStream(objectId));
        }
    }

    /**
     * The {@link Tree}s {@link ObjectId} to load.
     */
    public ObjectLoadOperation setObject(ObjectId obj) {
        this.objectId = obj;
        return this;
    }

}

package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.InputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.ObjectDatabase;

/**
 * Loads the specified {@link ObjectId} from its underlying storage in the
 * {@link ObjectDatabase} of the {@link BHive}.
 */
public class ObjectLoadOperation extends BHive.Operation<InputStream> {

    private ObjectId objectId;

    @Override
    public InputStream call() throws Exception {
        assertNotNull(objectId, "Object to load not set");
        return getObjectManager().db(x -> x.getStream(objectId));
    }

    /**
     * The {@link Tree}s {@link ObjectId} to load.
     */
    public ObjectLoadOperation setObject(ObjectId obj) {
        this.objectId = obj;
        return this;
    }

}

package io.bdeploy.bhive.objects;

import java.nio.file.Path;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;

public class ReadOnlyObjectDatabase extends ObjectDatabase {

    public ReadOnlyObjectDatabase(Path root, ActivityReporter reporter) {
        // no tmp and no transactions - only required on writable databases.
        super(root, null, reporter, null);
    }

    @Override
    protected ObjectId internalAddObject(ObjectWriter writer) {
        throw new UnsupportedOperationException("Read-only Database");
    }

    @Override
    public void removeObject(ObjectId id) {
        throw new UnsupportedOperationException("Read-only Database");
    }
}

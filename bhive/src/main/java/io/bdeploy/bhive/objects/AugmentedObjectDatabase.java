package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;

/**
 * An {@link ObjectDatabase} which can be augmented using an additional {@link ReadOnlyObjectDatabase}.
 * <p>
 * Objects will be consumed first from the {@link ReadOnlyObjectDatabase} and then from the local one.
 * <p>
 * Object will <b>always</b> be added to the local database.
 * <p>
 * Removing an object will either remove it locally or silently do nothing.
 */
public class AugmentedObjectDatabase extends ObjectDatabase {

    private final ReadOnlyObjectDatabase augment;

    public AugmentedObjectDatabase(Path root, Path tmp, ActivityReporter reporter, BHiveTransactions transactions,
            ReadOnlyObjectDatabase augment) {
        super(root, tmp, reporter, transactions);
        this.augment = augment;
    }

    @Override
    public Path getObjectFile(ObjectId id) {
        if (augment != null && augment.hasObject(id)) {
            return augment.getObjectFile(id);
        }
        return super.getObjectFile(id);
    }

    @Override
    public InputStream getStream(ObjectId id) throws IOException {
        if (augment != null && augment.hasObject(id)) {
            return augment.getStream(id);
        }
        return super.getStream(id);
    }

}

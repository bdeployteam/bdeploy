package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.audit.AuditParameterExtractor.NoAudit;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * The {@link CopyOperation} copies objects and manifests from one local
 * {@link BHive} to another local {@link BHive}.
 * <p>
 * If no {@link Manifest} or {@link ObjectId} is set, the whole contents of the
 * local {@link BHive} the operation is executed on will be copied.
 */
public class CopyOperation extends BHive.Operation<TransferStatistics> {

    @AuditWith(AuditStrategy.COLLECTION_SIZE)
    private final SortedSet<ObjectId> objects = new TreeSet<>();

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();

    @NoAudit
    private BHiveExecution destinationHive;
    private boolean partialAllowed;

    @Override
    public TransferStatistics call() throws Exception {
        TransferStatistics result = new TransferStatistics();
        Instant start = Instant.now();

        assertNotNull(destinationHive, "Destination Hive not set");

        try (Activity activity = getActivityReporter().start("Copying Objects");
                Transaction t = destinationHive.getTransactions().begin()) {
            if (objects.isEmpty() && manifests.isEmpty()) {
                // copy all from the local hive; don't check which are reachable from manifests,
                // as manifest may not be consistent in the source hive (delta transfer).
                execute(new ManifestListOperation()).forEach(manifests::add);
                execute(new ObjectListOperation().addManifest(manifests)).forEach(objects::add);
            }

            result.sumManifests = manifests.size();
            result.sumMissingObjects = objects.size();

            // Scan for referenced manifests. Referenced manifests are found transitively.
            SortedSet<Manifest.Key> additional = new TreeSet<>();
            manifests.forEach(m -> additional
                    .addAll(execute(new ManifestRefScanOperation().setAllowMissingObjects(true).setManifest(m)).values()));
            manifests.addAll(additional);

            if (!objects.isEmpty()) {
                InsertExistingObjectsOperation destinationInsert = new InsertExistingObjectsOperation()
                        .setSourceObjectManager(getObjectManager());
                objects.forEach(destinationInsert::addObject);
                destinationHive.execute(destinationInsert);
            }

            if (!manifests.isEmpty()) {
                ManifestDatabase mdb = getManifestDatabase();
                List<Manifest> loaded = manifests.stream().map(mdb::getManifest).collect(Collectors.toList());

                InsertManifestOperation destinationManifestInsert = new InsertManifestOperation();
                loaded.forEach(destinationManifestInsert::addManifest);
                destinationHive.execute(destinationManifestInsert);

                if (!partialAllowed) {
                    // check manifests, REMOVE them in case they are damaged to not block future operations.
                    ManifestConsistencyCheckOperation destinationCheck = new ManifestConsistencyCheckOperation().setDryRun(false);
                    manifests.forEach(destinationCheck::addRoot);
                    destinationHive.execute(destinationCheck);
                }
            }
        } finally {
            result.duration = Duration.between(start, Instant.now()).toMillis();
        }

        return result;
    }

    /**
     * Add an {@link ObjectId} to be copied into the destination hive
     */
    public CopyOperation addObject(ObjectId object) {
        objects.add(object);
        return this;
    }

    /**
     * Add an {@link ObjectId}s to be copied into the destination hive
     */
    public CopyOperation addObject(Collection<ObjectId> objectIds) {
        this.objects.addAll(objectIds);
        return this;
    }

    /**
     * Adds multiple {@link Manifest}s to be inserted into the destination hive.
     * <p>
     * CAUTION: this will only insert the manifest, NO object which is
     * required/referenced by the manifest will be copied automatically. All
     * {@link ObjectId}s to be copied need to be added using
     * {@link #addObject(ObjectId)}.
     */
    public CopyOperation addManifest(Collection<Manifest.Key> keys) {
        manifests.addAll(keys);
        return this;
    }

    /**
     * Add a {@link Manifest} to be inserted into the destination hive.
     * <p>
     * CAUTION: this will only insert the manifest, NO object which is
     * required/referenced by the manifest will be copied automatically. All
     * {@link ObjectId}s to be copied need to be added using
     * {@link #addObject(ObjectId)}.
     */
    public CopyOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    /**
     * If a partial destination hive is allowed (default <code>false</code>), no
     * consistency check on the destination hive will be performed after copying
     * objects. Normally a quick consistency (reachability of referenced objects for
     * transfered manifests) will be performed to assure all is well.
     */
    public CopyOperation setPartialAllowed(boolean allowed) {
        partialAllowed = allowed;
        return this;
    }

    /**
     * The destination {@link BHive}.
     */
    public CopyOperation setDestinationHive(BHiveExecution hive) {
        destinationHive = hive;
        return this;
    }

}

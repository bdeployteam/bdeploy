package io.bdeploy.bhive.op;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import com.google.common.io.CountingInputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.audit.AuditParameterExtractor.NoAudit;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.SortManifestsByReferences;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.FixedLengthStream;
import io.bdeploy.common.util.ReportingInputStream;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StreamHelper;

/**
 * Reads one or more objects from a stream and inserts them into the local hive.
 */
public class ObjectReadOperation extends BHive.TransactedOperation<TransferStatistics> {

    @NoAudit
    private InputStream input;
    private boolean isSyncEnabled = false;

    @Override
    public TransferStatistics callTransacted() throws Exception {
        TransferStatistics result = new TransferStatistics();
        Instant start = Instant.now();
        RuntimeAssert.assertNotNull(input);
        try (CountingInputStream countingIn = new CountingInputStream(input);
                BufferedInputStream buffIn = new BufferedInputStream(countingIn, ObjectWriteOperation.BUFFER_SIZE * 2);
                GZIPInputStream zipIn = new GZIPInputStream(buffIn, ObjectWriteOperation.BUFFER_SIZE);
                DataInputStream dataIn = new DataInputStream(zipIn)) {
            long totalSize = dataIn.readLong();

            String baseActivity = "Receiving";
            ManifestConsistencyCheckOperation checkOp = new ManifestConsistencyCheckOperation();
            try (Activity activity = getActivityReporter().start(baseActivity, totalSize);
                    ReportingInputStream reportingIn = new ReportingInputStream(dataIn, totalSize, activity, baseActivity)) {
                SortedSet<ObjectId> objects = new TreeSet<>();
                SortedSet<Manifest> manifests = new TreeSet<>();

                // Read all manifests from the stream
                long counter = dataIn.readLong();
                for (int i = 0; i < counter; i++) {
                    long size = dataIn.readLong();
                    Manifest mf = StorageHelper.fromStream(new FixedLengthStream(reportingIn, size), Manifest.class);
                    manifests.add(mf);
                    checkOp.addRoot(mf.getKey());
                }

                // Read all objects from the stream
                counter = dataIn.readLong();
                for (int i = 0; i < counter; i++) {
                    long size = dataIn.readLong();
                    ObjectId insertedId = getObjectManager().db(db -> db.addObject(new FixedLengthStream(reportingIn, size)));
                    objects.add(insertedId);
                }
                result.sumMissingObjects = counter;

                // Insert manifests as last operation - sorted by references they may have to each other.
                manifests.stream().sorted(new SortManifestsByReferences()).forEach(mf -> {
                    if (!getManifestDatabase().isManifestInSync(mf.getKey(), mf.getRoot(), isSyncEnabled)) {
                        getManifestDatabase().addManifest(mf, isSyncEnabled);
                        result.sumManifests++;
                    }
                });

                result.duration = Duration.between(start, Instant.now()).toMillis();
            }
            // Check manifests for consistency and remove invalid ones
            Set<ElementView> damaged = execute(checkOp.setDryRun(false));
            if (!damaged.isEmpty()) {
                throw new IllegalStateException(
                        "Failed to stream all required objects. Removed " + damaged.size() + " missing/damaged elements.");
            }
            result.transferSize = countingIn.getCount();
        } finally {
            if (result.duration == 0) {
                // fallback only if no duration has been calculated (aborted, failure, etc.).
                result.duration = Duration.between(start, Instant.now()).toMillis();
            }
            StreamHelper.close(input);
        }
        return result;
    }

    /**
     * The stream to read the objects from. The stream is closed at the end of the operation
     */
    public ObjectReadOperation stream(InputStream input) {
        this.input = input;
        return this;
    }

    public ObjectReadOperation setSyncEnabled(boolean isSyncEnabled) {
        this.isSyncEnabled = isSyncEnabled;
        return this;
    }

}

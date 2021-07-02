package io.bdeploy.bhive.op;

import java.io.DataInputStream;
import java.io.IOException;
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
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StreamHelper;

/**
 * Reads one or more objects from a stream and inserts them into the local hive.
 */
public class ObjectReadOperation extends BHive.TransactedOperation<TransferStatistics> {

    @NoAudit
    private InputStream input;

    @Override
    public TransferStatistics callTransacted() throws Exception {
        TransferStatistics result = new TransferStatistics();
        Instant start = Instant.now();
        RuntimeAssert.assertNotNull(input);
        try (CountingInputStream countingIn = new CountingInputStream(input);
                GZIPInputStream zipIn = new GZIPInputStream(countingIn);
                DataInputStream dataIn = new DataInputStream(zipIn)) {
            long maxWork = dataIn.readLong();

            String baseActivity = "Receiving";
            try (Activity activity = getActivityReporter().start(baseActivity, maxWork)) {
                SortedSet<ObjectId> objects = new TreeSet<>();
                SortedSet<Manifest> manifests = new TreeSet<>();

                // Read all manifests from the stream
                ManifestConsistencyCheckOperation checkOp = new ManifestConsistencyCheckOperation();
                long counter = dataIn.readLong();
                for (int i = 0; i < counter; i++) {
                    long size = dataIn.readLong();
                    Manifest mf = StorageHelper.fromStream(new FixedLengthStream(dataIn, size), Manifest.class);
                    manifests.add(mf);
                    activity.worked(1);
                    checkOp.addRoot(mf.getKey());
                    activity.activity(baseActivity + ": " + FormatHelper.formatTransferRate(countingIn.getCount(),
                            Duration.between(start, Instant.now()).toMillis()));
                }

                // Read all objects from the stream
                counter = dataIn.readLong();
                for (int i = 0; i < counter; i++) {
                    long size = dataIn.readLong();
                    ObjectId insertedId = getObjectManager().db(db -> db.addObject(new FixedLengthStream(dataIn, size)));
                    objects.add(insertedId);
                    activity.worked(1);
                    activity.activity(baseActivity + ": " + FormatHelper.formatTransferRate(countingIn.getCount(),
                            Duration.between(start, Instant.now()).toMillis()));
                }
                result.sumMissingObjects = counter;

                // Insert manifests as last operation
                manifests.forEach(mf -> {
                    if (!getManifestDatabase().hasManifest(mf.getKey())) {
                        getManifestDatabase().addManifest(mf);
                        result.sumManifests++;
                    }
                });

                // Check manifests for consistency and remove invalid ones
                Set<ElementView> damaged = execute(checkOp.setDryRun(false));
                if (!damaged.isEmpty()) {
                    throw new IllegalStateException(
                            "Failed to stream all required objects. Removed " + damaged.size() + " missing/damaged elements.");
                }
            }
            result.transferSize = countingIn.getCount();
        } finally {
            StreamHelper.close(input);
            result.duration = Duration.between(start, Instant.now()).toMillis();
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

    /**
     * An input stream that reads a given amount of bytes from a stream.
     */
    private static class FixedLengthStream extends InputStream {

        /** The underlying stream to read from */
        private final InputStream in;

        /** The number of bytes to read from the stream */
        private long remaining;

        protected FixedLengthStream(InputStream in, long totalSize) {
            this.in = in;
            this.remaining = totalSize;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int num = read(single, 0, 1);
            return num == -1 ? -1 : (single[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            // Signal end of stream if we have consumed all bytes
            if (remaining <= 0) {
                return -1;
            }

            // Read up-to the remaining size
            long bytesToRead = Math.min(len, remaining);
            int numRead = in.read(b, off, (int) bytesToRead);
            if (numRead == -1) {
                throw new IOException("Unexpected end of stream. Expecting '" + remaining + "' bytes.");
            }

            // Provide the number of bytes that where read
            remaining -= numRead;
            return numRead;
        }

    }

}

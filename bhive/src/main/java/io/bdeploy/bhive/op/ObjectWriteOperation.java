package io.bdeploy.bhive.op;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CountingOutputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditStrategy;
import io.bdeploy.bhive.audit.AuditParameterExtractor.AuditWith;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StreamHelper;

/**
 * Writes one or more objects to a stream.
 */
@ReadOnlyOperation
public class ObjectWriteOperation extends BHive.Operation<Long> {

    public static final Logger log = LoggerFactory.getLogger(ObjectWriteOperation.class);
    public static final int BUFFER_SIZE = 8192;

    @AuditWith(AuditStrategy.COLLECTION_PEEK)
    private final Set<ObjectId> objects = new LinkedHashSet<>();

    private final Set<Manifest.Key> manifests = new LinkedHashSet<>();

    private OutputStream output;

    @Override
    public Long call() throws Exception {
        RuntimeAssert.assertNotNull(output);

        Set<Manifest.Key> missing = new TreeSet<>();

        for (Manifest.Key key : new ArrayList<>(manifests)) {
            if (!Boolean.TRUE.equals(execute(new ManifestExistsOperation().setManifest(key)))) {
                // manifest is missing for whatever reason, we warn about it and don't send anything related to it.
                // the caller can find manifest keys which are not in the result set but in the request to figure out
                // if certain manifests where left out.
                log.warn("Cannot send manifest {} because it is missing from BHive", key);
                missing.add(key);
                continue;
            }
            Collection<Key> refs = execute(new ManifestRefScanOperation().setAllowMissingObjects(true).setManifest(key)).values();
            manifests.addAll(refs);
        }

        // filter out missing manifests to avoid failure in this case.
        manifests.removeAll(missing);

        // Collect the entire size that we are going to transfer.
        // ATTENTION: insertion order is important.
        long totalSize = 0;
        Map<ObjectId, Long> object2FileSize = new LinkedHashMap<>();
        for (ObjectId object : objects) {
            long size = getObjectManager().db(db -> db.getObjectSize(object));
            object2FileSize.put(object, size);
            totalSize += size;
        }

        try (Activity activity = getActivityReporter().start("Sending", totalSize);
                CountingOutputStream countingOut = new CountingOutputStream(output);
                GZIPOutputStream zipOut = new GZIPOutputStream(countingOut, BUFFER_SIZE);
                BufferedOutputStream buffOut = new BufferedOutputStream(zipOut, BUFFER_SIZE * 2);
                DataOutputStream dataOut = new DataOutputStream(zipOut)) {

            // First we send the total size so that the client can display a progress bar
            dataOut.writeLong(totalSize);

            // Stream all manifests
            dataOut.writeLong(manifests.size());
            for (Manifest.Key key : manifests) {
                Manifest mf = getManifestDatabase().getManifest(key);
                byte[] bytes = StorageHelper.toRawBytes(mf);
                dataOut.writeLong(bytes.length);
                dataOut.write(bytes);
            }

            // Stream all objects
            dataOut.writeLong(objects.size());
            for (Map.Entry<ObjectId, Long> entry : object2FileSize.entrySet()) {
                ObjectId objectId = entry.getKey();
                long size = entry.getValue();
                getObjectManager().db(db -> {
                    dataOut.writeLong(size);
                    try (InputStream input = db.getStream(objectId)) {
                        StreamHelper.copy(input, dataOut);
                    }
                    return null;
                });
                activity.worked(size);
            }
            return countingOut.getCount();
        } finally {
            StreamHelper.close(output);
        }
    }

    /**
     * The stream to write the objects to
     */
    public ObjectWriteOperation stream(OutputStream output) {
        this.output = output;
        return this;
    }

    /**
     * The {@link ObjectId object} to fetch
     */
    public ObjectWriteOperation objects(ObjectId obj) {
        this.objects.add(obj);
        return this;
    }

    /**
     * The {@link ObjectId objects} to fetch
     */
    public ObjectWriteOperation objects(Collection<ObjectId> objects) {
        this.objects.addAll(objects);
        return this;
    }

    /**
     * The {@link Key manifests} to fetch
     */
    public ObjectWriteOperation manifest(Manifest.Key obj) {
        this.manifests.add(obj);
        return this;
    }

    /**
     * The {@link Key manifests} to fetch
     */
    public ObjectWriteOperation manifests(Collection<Manifest.Key> manifests) {
        this.manifests.addAll(manifests);
        return this;
    }

}

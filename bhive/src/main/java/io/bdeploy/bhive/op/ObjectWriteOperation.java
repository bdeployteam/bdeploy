package io.bdeploy.bhive.op;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import com.google.common.io.CountingOutputStream;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
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

    private final Set<ObjectId> objects = new LinkedHashSet<>();
    private final Set<Manifest.Key> manifests = new LinkedHashSet<>();

    private OutputStream output;

    @Override
    public Long call() throws Exception {
        RuntimeAssert.assertNotNull(output);

        for (Manifest.Key key : new ArrayList<>(manifests)) {
            Collection<Key> refs = execute(new ManifestRefScanOperation().setAllowMissingObjects(true).setManifest(key)).values();
            manifests.addAll(refs);
        }

        int maxWork = manifests.size() + objects.size();
        try (Activity activity = getActivityReporter().start("Writing objects...", maxWork);
                CountingOutputStream countingOut = new CountingOutputStream(output);
                GZIPOutputStream zipOut = new GZIPOutputStream(countingOut);
                DataOutputStream dataOut = new DataOutputStream(zipOut)) {
            dataOut.writeLong(maxWork);

            // Stream all manifests
            dataOut.writeLong(manifests.size());
            for (Manifest.Key key : manifests) {
                Manifest mf = getManifestDatabase().getManifest(key);
                byte[] bytes = StorageHelper.toRawBytes(mf);
                dataOut.writeLong(bytes.length);
                dataOut.write(bytes);
                activity.worked(1);
            }

            // Stream all objects
            dataOut.writeLong(objects.size());
            for (ObjectId object : objects) {
                getObjectManager().db(db -> {
                    long size = db.getObjectSize(object);
                    dataOut.writeLong(size);
                    try (InputStream input = db.getStream(object)) {
                        StreamHelper.copy(input, dataOut);
                    }
                    return null;
                });
                activity.worked(1);
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

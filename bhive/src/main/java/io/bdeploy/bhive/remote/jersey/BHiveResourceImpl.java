package io.bdeploy.bhive.remote.jersey;

import java.io.InputStream;
import java.util.Set;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.LocalBHiveAdapter;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;

public class BHiveResourceImpl implements BHiveResource {

    private static final Logger log = LoggerFactory.getLogger(BHiveResourceImpl.class);

    private final LocalBHiveAdapter wrapper;

    public BHiveResourceImpl(BHive hive, ActivityReporter reporter) {
        this.wrapper = new LocalBHiveAdapter(hive, reporter);
    }

    @Override
    public Set<ObjectId> getMissingObjects(Set<ObjectId> all) {
        return wrapper.getMissingObjects(all);
    }

    @Override
    public SortedMap<Key, ObjectId> getManifestInventory(String... names) {
        return wrapper.getManifestInventory(names);
    }

    @Override
    public void removeManifest(Key key) {
        wrapper.removeManifest(key);
    }

    @Override
    public void prune() {
        wrapper.prune();
    }

    @Override
    public Set<ObjectId> getRequiredObjects(ObjectListSpec spec) {
        return wrapper.getRequiredObjects(spec.trees, spec.excludeTrees);
    }

    @Override
    public Set<ObjectId> getRequiredTrees(ObjectId tree) {
        return wrapper.getRequiredTrees(tree);
    }

    @Override
    public void push(java.nio.file.Path zipedHive) {
        try {
            wrapper.push(zipedHive);
        } finally {
            try {
                PathHelper.deleteIfExistsRetry(zipedHive);
            } catch (Exception e) {
                log.warn("cannot delete {}", zipedHive);
                if (log.isDebugEnabled()) {
                    log.debug("Exception: ", e);
                }
            }
        }
    }

    @Override
    public TransferStatistics pushAsStream(InputStream in) {
        return wrapper.pushAsStream(in);
    }

    @Override
    @DeleteAfterWrite
    public java.nio.file.Path fetch(FetchSpec spec) {
        return wrapper.fetch(spec.requiredObjects, spec.manifestsToFetch);
    }

    @Override
    public InputStream fetchAsStream(FetchSpec spec) {
        return wrapper.fetchAsStream(spec.requiredObjects, spec.manifestsToFetch);
    }

}

package io.bdeploy.bhive.remote.jersey;

import java.io.IOException;
import java.nio.file.Files;
import java.util.SortedMap;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.remote.LocalBHiveAdapter;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;

public class BHiveResourceImpl implements BHiveResource {

    private static final Logger log = LoggerFactory.getLogger(BHiveResourceImpl.class);

    private final LocalBHiveAdapter wrapper;

    public BHiveResourceImpl(BHive hive, ActivityReporter reporter) {
        this.wrapper = new LocalBHiveAdapter(hive, reporter);
    }

    @Override
    public SortedSet<ObjectId> getMissingObjects(SortedSet<ObjectId> all) {
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
    public SortedSet<ObjectId> getRequiredObjects(ObjectListSpec spec) {
        return wrapper.getRequiredObjects(spec.trees, spec.excludeTrees);
    }

    @Override
    public SortedSet<ObjectId> getRequiredTrees(ObjectId tree) {
        return wrapper.getRequiredTrees(tree);
    }

    @Override
    public void push(java.nio.file.Path zipedHive) {
        try {
            wrapper.push(zipedHive);
        } finally {
            try {
                Files.delete(zipedHive);
            } catch (IOException e) {
                log.warn("cannot delete " + zipedHive);
                log.debug("Exception: ", e);
            }
        }
    }

    @DeleteAfterWrite
    @Override
    public java.nio.file.Path fetch(FetchSpec spec) {
        return wrapper.fetch(spec.requiredObjects, spec.manifestsToFetch);
    }

}

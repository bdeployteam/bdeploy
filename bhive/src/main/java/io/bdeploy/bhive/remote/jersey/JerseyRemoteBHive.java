package io.bdeploy.bhive.remote.jersey;

import java.nio.file.Path;
import java.util.SortedMap;
import java.util.SortedSet;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveResource.FetchSpec;
import io.bdeploy.bhive.remote.jersey.BHiveResource.ObjectListSpec;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;

/**
 * Adapts a {@link BHiveResource} using the {@link JerseyClientFactory} to the {@link RemoteBHive} interface.
 */
public class JerseyRemoteBHive implements RemoteBHive {

    public static final BHiveJacksonModule HIVE_JACKSON_MODULE = new BHiveJacksonModule();
    public static final String DEFAULT_NAME = "default";

    private final BHiveResource client;

    public JerseyRemoteBHive(RemoteService service, String name, ActivityReporter reporter) {
        JerseyClientFactory jcf = JerseyClientFactory.get(service);

        jcf.register(HIVE_JACKSON_MODULE);
        jcf.setReporter(reporter);

        this.client = jcf.getProxyClient(BHiveLocator.class).getNamedHive(name == null ? DEFAULT_NAME : name);
    }

    @Override
    public SortedSet<ObjectId> getMissingObjects(SortedSet<ObjectId> all) {
        return client.getMissingObjects(all);
    }

    @Override
    public SortedMap<Key, ObjectId> getManifestInventory(String... names) {
        return client.getManifestInventory(names);
    }

    @Override
    public void removeManifest(Key key) {
        client.removeManifest(key);
    }

    @Override
    public void prune() {
        client.prune();
    }

    @Override
    public SortedSet<ObjectId> getRequiredObjects(SortedSet<ObjectId> trees, SortedSet<ObjectId> excludeTrees) {
        ObjectListSpec spec = new ObjectListSpec();
        spec.trees = trees;
        spec.excludeTrees = excludeTrees;
        return client.getRequiredObjects(spec);
    }

    @Override
    public SortedSet<ObjectId> getRequiredTrees(ObjectId tree) {
        return client.getRequiredTrees(tree);
    }

    @Override
    public void push(Path zipedHive) {
        client.push(zipedHive);
    }

    @Override
    public Path fetch(SortedSet<ObjectId> availableObjects, SortedSet<Key> manifestsToFetch) {
        FetchSpec spec = new FetchSpec();
        spec.requiredObjects = availableObjects;
        spec.manifestsToFetch = manifestsToFetch;
        return client.fetch(spec);
    }

    @Override
    public void close() {
        // nothing to close in this case.
    }

}

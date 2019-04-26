package io.bdeploy.bhive.remote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectExistsOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.ActivityReporter;

/**
 * Adapts a local {@link BHive} to a {@link RemoteBHive}. This makes it possible
 * to specify local {@link BHive}s on the same machine as {@link RemoteBHive}.
 */
public class LocalBHiveAdapter implements RemoteBHive {

    private final BHive hive;
    private final ActivityReporter reporter;

    /**
     * Creates the adapter for the given {@link BHive}.
     */
    public LocalBHiveAdapter(BHive hive, ActivityReporter reporter) {
        this.hive = hive;
        this.reporter = reporter;
    }

    @Override
    public SortedSet<ObjectId> getMissingObjects(SortedSet<ObjectId> all) {
        ObjectExistsOperation findExisting = new ObjectExistsOperation();
        all.forEach(findExisting::addObject);
        SortedSet<ObjectId> known = hive.execute(findExisting);
        SortedSet<ObjectId> result = new TreeSet<>(all);
        result.removeAll(known);
        return result;
    }

    @Override
    public SortedSet<ObjectId> getRequiredObjects(SortedSet<ObjectId> trees, SortedSet<ObjectId> excludeTrees) {
        ObjectListOperation objectListOperation = new ObjectListOperation();
        trees.forEach(objectListOperation::addTree);
        excludeTrees.forEach(objectListOperation::excludeTree);
        return hive.execute(objectListOperation);
    }

    @Override
    public SortedSet<ObjectId> getRequiredTrees(ObjectId tree) {
        TreeView snapshot = hive.execute(new ScanOperation().setTree(tree));
        SortedSet<ObjectId> treeIds = new TreeSet<>();

        snapshot.visit(new TreeVisitor.Builder().onTree(x -> treeIds.add(x.getElementId())).build());

        return treeIds;
    }

    @Override
    public SortedMap<Manifest.Key, ObjectId> getManifestInventory(String... names) {
        SortedSet<Manifest.Key> mfs = new TreeSet<>();
        if (names.length == 0) {
            mfs.addAll(hive.execute(new ManifestListOperation()));
        } else {
            for (String name : names) {
                mfs.addAll(hive.execute(new ManifestListOperation().setManifestName(name)));
            }
        }
        SortedMap<Manifest.Key, ObjectId> result = new TreeMap<>();

        for (Manifest.Key key : mfs) {
            result.put(key, hive.execute(new ManifestLoadOperation().setManifest(key)).getRoot());
        }

        return result;
    }

    @Override
    public void removeManifest(Key key) {
        hive.execute(new ManifestDeleteOperation().setToDelete(key));
    }

    @Override
    public void prune() {
        hive.execute(new PruneOperation());
    }

    @Override
    public void push(Path zipedHive) {
        if (!Files.exists(zipedHive)) {
            throw new IllegalArgumentException("File does not exist: " + zipedHive);
        }

        try (BHive packed = new BHive(UriBuilder.fromUri("jar:" + zipedHive.toUri()).build(), reporter)) {
            packed.execute(new CopyOperation().setDestinationHive(hive).setPartialAllowed(false));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot push to local repository", e);
        }
    }

    @Override
    public Path fetch(SortedSet<ObjectId> requiredObjects, SortedSet<Manifest.Key> manifestsToFetch) {
        try {
            // assume no manifests are present in the target. filtering must happen before
            // calling fetch.
            Path tmpHive = Files.createTempFile("fetch-", ".zip");
            Files.delete(tmpHive); // need to delete to re-create with ZipFileSystem

            try (BHive emptyHive = new BHive(UriBuilder.fromUri("jar:" + tmpHive.toUri()).build(), reporter)) {
                CopyOperation op = new CopyOperation().setDestinationHive(emptyHive).setPartialAllowed(true);
                requiredObjects.forEach(op::addObject);
                manifestsToFetch.forEach(op::addManifest);

                hive.execute(op); // perform copy.
            } // important: close hive to sync with filesystem

            return tmpHive;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot fetch from local repository", e);
        }
    }

    @Override
    public void close() {
        hive.close();
    }

}

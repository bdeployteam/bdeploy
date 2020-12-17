package io.bdeploy.bhive.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import jakarta.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectExistsOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectReadOperation;
import io.bdeploy.bhive.op.ObjectWriteOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Adapts a local {@link BHive} to a {@link RemoteBHive}. This makes it possible
 * to specify local {@link BHive}s on the same machine as {@link RemoteBHive}.
 */
public class LocalBHiveAdapter implements RemoteBHive {

    private static final Logger log = LoggerFactory.getLogger(LocalBHiveAdapter.class);

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
    public Set<ObjectId> getMissingObjects(Set<ObjectId> all) {
        try (Activity activity = reporter.start("Scanning for missing objects...")) {
            return hive.execute(new ObjectExistsOperation().addAll(all)).missing;
        }
    }

    @Override
    public Set<ObjectId> getRequiredObjects(Set<ObjectId> trees, Set<ObjectId> excludeTrees) {
        return hive.execute(new ObjectListOperation().addTree(trees).excludeTree(excludeTrees));
    }

    @Override
    public Set<ObjectId> getRequiredTrees(ObjectId tree) {
        TreeView snapshot = hive.execute(new ScanOperation().setTree(tree));
        Set<ObjectId> treeIds = new LinkedHashSet<>();

        snapshot.visitDfs(new TreeVisitor.Builder().onTree(x -> treeIds.add(x.getElementId())).build());

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
    public TransferStatistics pushAsStream(InputStream in) {
        return hive.execute(new ObjectReadOperation().stream(in));
    }

    @Override
    public Path fetch(Set<ObjectId> requiredObjects, Set<Manifest.Key> manifestsToFetch) {
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
    public InputStream fetchAsStream(Set<ObjectId> objects, Set<Manifest.Key> manifests) {
        PipedInputStream input = new PipedInputStream();
        CompletableFuture<Void> barrier = new CompletableFuture<>();

        Thread thread = new Thread(() -> {
            try (PipedOutputStream output = new PipedOutputStream(input)) {
                barrier.complete(null);
                hive.execute(new ObjectWriteOperation().stream(output).manifests(manifests).objects(objects));
            } catch (IOException e) {
                log.warn("Cannot fully send content to fetching client via stream", e);
            }
        });
        thread.setDaemon(true);
        thread.setName("Write-Objects");
        thread.start();

        barrier.join();
        return input;
    }

    @Override
    public void close() {
        hive.close();
    }

}

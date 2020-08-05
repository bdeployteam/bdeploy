package io.bdeploy.bhive.op.remote;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectWriteOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Pushes manifests from the local {@link BHive} to a remote {@link BHive}. If no
 * manifests are given, all locally available manifests are pushed.
 */
@ReadOnlyOperation
public class PushOperation extends RemoteOperation<TransferStatistics, PushOperation> {

    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private String hiveName;

    @Override
    public TransferStatistics call() throws Exception {
        TransferStatistics stats = new TransferStatistics();

        try (Activity activity = getActivityReporter().start("Pushing manifests...", -1)) {
            if (manifests.isEmpty()) {
                manifests.addAll(execute(new ManifestListOperation()));
            }

            try (RemoteBHive rh = RemoteBHive.forService(getRemote(), hiveName, getActivityReporter())) {

                // add all referenced manifests
                manifests.addAll(manifests.parallelStream()
                        .flatMap(m -> execute(new ManifestRefScanOperation().setManifest(m)).values().stream())
                        .collect(Collectors.toSet()));

                // read remote inventory
                SortedMap<Manifest.Key, ObjectId> remoteManifests = rh
                        .getManifestInventory(manifests.parallelStream().map(Manifest.Key::toString).toArray(String[]::new));

                // remove all manifests that already exist
                manifests.removeIf(m -> remoteManifests.containsKey(m));

                if (manifests.isEmpty()) {
                    return stats;
                }

                stats.sumManifests = manifests.size();

                // create a view of every manifest on our side. does not follow references as references are scanned above.
                List<TreeView> snapshots = manifests.stream()
                        .map(m -> execute(new ScanOperation().setManifest(m).setFollowReferences(false)))
                        .collect(Collectors.toList());

                // STEP 1: figure out all trees we want to push - scans without following references
                SortedSet<TreeView> allTreeSnapshots = scanAllTreeSnapshots(snapshots);
                SortedSet<ObjectId> allTrees = allTreeSnapshots.parallelStream().map(TreeView::getElementId)
                        .collect(Collectors.toCollection(TreeSet::new));
                stats.sumTrees = allTrees.size();

                // STEP 2: ask the remote for missing trees
                SortedSet<ObjectId> missingTrees = rh.getMissingObjects(allTrees);
                stats.sumMissingTrees = missingTrees.size();

                // STEP 3: figure out (locally) which trees are already present on the remote.
                SortedSet<TreeView> missingTreeSnapshots = allTreeSnapshots.parallelStream()
                        .filter(t -> missingTrees.contains(t.getElementId())).collect(Collectors.toCollection(TreeSet::new));

                // STEP 4: scan all trees, exclude trees already present.
                //scan objectIds of each missingTree, include missingTree itself
                SortedSet<ObjectId> requiredObjects = scanAllObjectSnapshots(missingTreeSnapshots);

                // STEP 5: filter object to transfer only what is REALLY required
                SortedSet<ObjectId> missingObjects = rh.getMissingObjects(requiredObjects);
                stats.sumMissingObjects = missingObjects.size();

                // STEP 6: copy objects and manifests
                stats.transferSize = push(rh, missingObjects, manifests);
            }
        }
        return stats;
    }

    /**
     * Find all Trees within the list of {@link TreeView}s recursively.
     */
    private SortedSet<TreeView> scanAllTreeSnapshots(List<TreeView> toPush) {
        SortedSet<TreeView> allTrees = new TreeSet<>();
        TreeVisitor visitor = new TreeVisitor.Builder().onTree(t -> {
            if (t instanceof ManifestRefView) {
                return false;
            }
            allTrees.add(t);
            return true;
        }).build();
        for (TreeView snapshot : toPush) {
            snapshot.visit(visitor);
        }
        return allTrees;
    }

    /**
     * Find all {@link ObjectId}s referenced by the given trees (flat).
     * <p>
     * {@link ManifestRefView} is not followed, just the reference (since the reference is a standalone object just like a blob)
     * is recorded.
     */
    private SortedSet<ObjectId> scanAllObjectSnapshots(SortedSet<TreeView> missingTreeSnapshots) {
        return missingTreeSnapshots.parallelStream().map(t -> {
            SortedSet<ObjectId> objectsOfTree = new TreeSet<>();
            t.visit(new TreeVisitor.Builder().onTree(t::equals).onBlob(b -> objectsOfTree.add(b.getElementId()))
                    .onManifestRef(m -> objectsOfTree.add(m.getReferenceId())).build());
            objectsOfTree.add(t.getElementId());
            return objectsOfTree;
        }).flatMap(SortedSet::stream).collect(Collectors.toCollection(TreeSet::new));
    }

    public PushOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public PushOperation setHiveName(String name) {
        hiveName = name;
        return this;
    }

    private long push(RemoteBHive rh, SortedSet<ObjectId> objects, SortedSet<Key> manifests) throws IOException {
        try {
            return pushAsStream(rh, objects, manifests);
        } catch (UnsupportedOperationException ex) {
            return pushAsZip(rh, objects, manifests);
        }
    }

    private long pushAsZip(RemoteBHive rh, SortedSet<ObjectId> objects, SortedSet<Key> manifests) throws IOException {
        Path tmpHive = Files.createTempFile("push-", ".zip");
        Files.delete(tmpHive); // need to delete to re-create with ZipFileSystem

        try {
            try (BHive emptyHive = new BHive(UriBuilder.fromUri("jar:" + tmpHive.toUri()).build(), getActivityReporter())) {
                CopyOperation op = new CopyOperation().setDestinationHive(emptyHive).setPartialAllowed(true);
                objects.forEach(op::addObject);
                manifests.forEach(op::addManifest);

                execute(op); // perform copy.
            } // important: close hive to sync with filesystem

            long fileSize = Files.size(tmpHive);
            rh.push(tmpHive);
            return fileSize;
        } finally {
            Files.deleteIfExists(tmpHive);
        }
    }

    private long pushAsStream(RemoteBHive rh, SortedSet<ObjectId> objects, SortedSet<Key> manifests) throws IOException {
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream output = new PipedOutputStream(input);

        Thread thread = new Thread(() -> {
            execute(new ObjectWriteOperation().stream(output).manifests(manifests).objects(objects));
        });
        thread.setDaemon(true);
        thread.setName("Write-Objects");
        thread.start();

        return rh.pushAsStream(input);
    }

}

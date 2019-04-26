package io.bdeploy.bhive.op.remote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.remote.RemoteBHive;

/**
 * Pushes manifests from the local {@link BHive} to a remote {@link BHive}. If no
 * manifests are given, all locally available manifests are pushed.
 */
public class PushOperation extends RemoteOperation<TransferStatistics, PushOperation> {

    private final SortedSet<Manifest.Key> manifests = new TreeSet<>();
    private String hiveName;

    @Override
    public TransferStatistics call() throws Exception {
        TransferStatistics stats = new TransferStatistics();
        SortedSet<Manifest.Key> toPush = new TreeSet<>();

        if (manifests.isEmpty()) {
            manifests.addAll(execute(new ManifestListOperation()));
        }

        try (RemoteBHive rh = RemoteBHive.forService(getRemote(), hiveName, getActivityReporter())) {
            SortedMap<Manifest.Key, ObjectId> remoteManifests = rh
                    .getManifestInventory(manifests.stream().map(Manifest.Key::toString).toArray(String[]::new));

            for (Manifest.Key key : manifests) {
                if (remoteManifests.containsKey(key)) {
                    continue;
                }
                toPush.add(key);
            }

            // explicitly push and create on the remote any referenced manifests.
            manifests.forEach(m -> toPush.addAll(execute(new ManifestRefScanOperation().setManifest(m)).values()));

            if (toPush.isEmpty()) {
                return stats;
            }

            stats.sumManifests = toPush.size();

            // STEP 1: figure out all trees we want to push
            SortedSet<ObjectId> allTrees = scanAllTrees(toPush);
            stats.sumTrees = allTrees.size();

            // STEP 2: ask the remote for missing trees
            SortedSet<ObjectId> missingTrees = rh.getMissingObjects(allTrees);
            stats.sumMissingTrees = missingTrees.size();

            // STEP 3: figure out (locally) which trees are already present on the remote.
            SortedSet<ObjectId> presentTrees = new TreeSet<>(allTrees);
            presentTrees.removeAll(missingTrees);

            // STEP 4: scan all trees, exclude trees already present.
            ObjectListOperation scanWithExcludes = new ObjectListOperation();
            presentTrees.forEach(scanWithExcludes::excludeTree);
            missingTrees.forEach(scanWithExcludes::addTree);
            SortedSet<ObjectId> requiredObjects = execute(scanWithExcludes);

            // STEP 5: filter object to transfer only what is REALLY required
            SortedSet<ObjectId> missingObjects = rh.getMissingObjects(requiredObjects);
            stats.sumMissingObjects = missingObjects.size();

            // STEP 6: create temp hive, copy objects and manifests
            Path tmpHive = Files.createTempFile("push-", ".zip");
            Files.delete(tmpHive); // need to delete to re-create with ZipFileSystem

            try {
                try (BHive emptyHive = new BHive(UriBuilder.fromUri("jar:" + tmpHive.toUri()).build(), getActivityReporter())) {
                    CopyOperation op = new CopyOperation().setDestinationHive(emptyHive).setPartialAllowed(true);
                    missingObjects.forEach(op::addObject);
                    toPush.forEach(op::addManifest);

                    execute(op); // perform copy.
                } // important: close hive to sync with filesystem

                stats.transferSize = Files.size(tmpHive);
                rh.push(tmpHive);
            } finally {
                Files.deleteIfExists(tmpHive);
            }
        }
        return stats;
    }

    private SortedSet<ObjectId> scanAllTrees(SortedSet<Key> toPush) {
        SortedSet<ObjectId> allTrees = new TreeSet<>();
        for (Manifest.Key k : toPush) {
            TreeView snapshot = execute(new ScanOperation().setManifest(k));
            snapshot.visit(new TreeVisitor.Builder().onTree(x -> allTrees.add(x.getElementId())).build());
        }
        return allTrees;
    }

    public PushOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public PushOperation setHiveName(String name) {
        hiveName = name;
        return this;
    }

}

package io.bdeploy.bhive.op.remote;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ReadOnlyOperation;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.BlobView;
import io.bdeploy.bhive.objects.view.ElementView;
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
import jakarta.ws.rs.core.UriBuilder;

/**
 * Pushes manifests from the local {@link BHive} to a remote {@link BHive}. If no
 * manifests are given, all locally available manifests are pushed.
 */
@ReadOnlyOperation
public class PushOperation extends RemoteOperation<TransferStatistics, PushOperation> {

    private static final Logger log = LoggerFactory.getLogger(PushOperation.class);

    private final Set<Manifest.Key> manifests = new LinkedHashSet<>();
    private String hiveName;

    @Override
    public TransferStatistics call() throws Exception {
        TransferStatistics stats = new TransferStatistics();

        Instant start = Instant.now();
        try (Activity activity = getActivityReporter().start("Pushing", -1)) {
            try (RemoteBHive rh = RemoteBHive.forService(getRemote(), hiveName, getActivityReporter())) {
                // Add all local manifests if nothing is given
                if (manifests.isEmpty()) {
                    manifests.addAll(execute(new ManifestListOperation()));
                }

                // Add all referenced manifests
                Set<Manifest.Key> allManifests = new LinkedHashSet<>();
                for (Manifest.Key key : manifests) {
                    allManifests.addAll(execute(new ManifestRefScanOperation().setManifest(key)).values());
                    allManifests.add(key);
                }

                // Read remote inventory
                String[] manifestsAsArray = allManifests.stream().map(Manifest.Key::toString).toArray(String[]::new);
                SortedMap<Manifest.Key, ObjectId> manifest2Tree = rh.getManifestInventory(manifestsAsArray);

                // Remove all manifests that already exist
                allManifests.removeIf(manifest2Tree::containsKey);
                if (allManifests.isEmpty()) {
                    return stats;
                }

                // STEP 1: Figure out all trees we want to push - scans without following references
                Map<ObjectId, TreeView> allTrees = getAllTrees(allManifests);

                // STEP 2: Ask the remote for missing trees
                Set<ObjectId> missingTrees = rh.getMissingObjects(new LinkedHashSet<>(allTrees.keySet()));

                // STEP 3: Figure out which trees are already present on the remote.
                //         We reverse the list at the end so that leaves are first followed by their parents
                List<TreeView> missingTreeViews = allTrees.values().stream().filter(t -> missingTrees.contains(t.getElementId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.reverse(missingTreeViews);

                // STEP 4: Figure out which objects are required for the trees
                Set<ObjectId> requiredObjects = getRequiredObjects(missingTreeViews);

                // STEP 5: filter object to transfer only what is REALLY required
                Set<ObjectId> missingObjects = rh.getMissingObjects(requiredObjects);

                // STEP 6: copy objects and manifests
                TransferStatistics pushStats = push(rh, missingObjects, allManifests);

                // Update statistics with some new knowledge.
                stats.sumTrees = allTrees.size();
                stats.sumManifests = allManifests.size();
                stats.sumMissingTrees = missingTrees.size();
                stats.transferSize = pushStats.transferSize;
                stats.sumMissingObjects = missingObjects.size();
            }
        } finally {
            stats.duration = Duration.between(start, Instant.now()).toMillis();
        }
        return stats;
    }

    /**
     * Find all trees for the given list of manifests
     */
    private Map<ObjectId, TreeView> getAllTrees(Set<Manifest.Key> manifests) {
        Map<ObjectId, TreeView> allTrees = new LinkedHashMap<>();
        for (Manifest.Key manifest : manifests) {
            TreeView view = execute(new ScanOperation().setManifest(manifest).setFollowReferences(false));
            view.visit(new TreeVisitor.Builder().onTree(t -> {
                if (t instanceof ManifestRefView) {
                    return false;
                }
                allTrees.put(t.getElementId(), t);
                return true;
            }).build());
        }
        return allTrees;
    }

    /**
     * Find all {@link ObjectId}s referenced by the given trees (flat).
     */
    private Set<ObjectId> getRequiredObjects(List<TreeView> missingTrees) {
        Set<ObjectId> result = new LinkedHashSet<>();
        for (TreeView view : missingTrees) {
            for (ElementView child : view.getChildren().values()) {
                if (child instanceof BlobView) {
                    result.add(child.getElementId());
                } else if (child instanceof ManifestRefView) {
                    ManifestRefView refView = (ManifestRefView) child;
                    result.add(refView.getReferenceId());
                }
            }
            result.add(view.getElementId());
        }
        return result;
    }

    public PushOperation addManifest(Manifest.Key key) {
        manifests.add(key);
        return this;
    }

    public PushOperation addManifest(Collection<Manifest.Key> keys) {
        manifests.addAll(keys);
        return this;
    }

    public PushOperation setHiveName(String name) {
        hiveName = name;
        return this;
    }

    private TransferStatistics push(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) throws IOException {
        try {
            return pushAsStream(rh, objects, manifests);
        } catch (UnsupportedOperationException ex) {
            log.debug("Stream pushing not supported by target server", ex);
            return pushAsZip(rh, objects, manifests);
        }
    }

    private TransferStatistics pushAsZip(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) throws IOException {
        Path tmpHive = Files.createTempFile("push-", ".zip");
        Files.delete(tmpHive); // need to delete to re-create with ZipFileSystem

        try {
            TransferStatistics s;
            try (BHive emptyHive = new BHive(UriBuilder.fromUri("jar:" + tmpHive.toUri()).build(), getActivityReporter())) {
                CopyOperation op = new CopyOperation().setDestinationHive(emptyHive).setPartialAllowed(true);
                objects.forEach(op::addObject);
                manifests.forEach(op::addManifest);

                s = execute(op); // perform copy.
            } // important: close hive to sync with filesystem

            s.transferSize = Files.size(tmpHive);
            rh.push(tmpHive);
            return s;
        } finally {
            Files.deleteIfExists(tmpHive);
        }
    }

    private TransferStatistics pushAsStream(RemoteBHive rh, Set<ObjectId> objects, Set<Key> manifests) {
        PipedInputStream input = new PipedInputStream();
        CompletableFuture<Void> barrier = new CompletableFuture<>();

        Thread thread = new Thread(() -> {
            try (PipedOutputStream output = new PipedOutputStream(input)) {
                barrier.complete(null);
                execute(new ObjectWriteOperation().stream(output).manifests(manifests).objects(objects));
            } catch (Exception e) {
                log.warn("Cannot fully push content via stream", e);
            }
        });
        thread.setDaemon(true);
        thread.setName("Write-Objects");
        thread.start();

        barrier.join();
        return rh.pushAsStream(input);
    }

}

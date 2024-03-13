package io.bdeploy.bhive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive.Operation;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.ObjectReferenceDatabase;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.jersey.actions.Action;
import io.bdeploy.jersey.actions.ActionExecution;
import io.bdeploy.jersey.actions.ActionService;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;

/**
 * Capable of reorganizing a pool, given a set of input {@link BHive}s.
 */
public class BHivePoolOrganizer {

    private static final int PRUNE_LOOP_COUNT = 10000;

    private static final Logger log = LoggerFactory.getLogger(BHivePoolOrganizer.class);

    private final Path pool;
    private final int usageThreshold;
    private final ActionService as;

    private BHivePoolOrganizer(Path pool, int usageThreshold, ActionService as) {
        this.pool = pool.toAbsolutePath().normalize();
        this.usageThreshold = usageThreshold;
        this.as = as;
    }

    /**
     * Reorganizes the pool to match the given {@link BHive}s.
     * <p>
     * ATTENTION: The complete set of {@link BHive}s using this pool needs to be specified as this operation is destructive. It
     * will remove objects from the pool which are not referenced anymore from any of the given {@link BHive}s.
     *
     * @param bhives the set of BHives using this pool.
     */
    private void reorganize(Collection<BHive> bhives) {
        Path refDbRoot = pool.resolve(".refdb");

        if (PathHelper.exists(refDbRoot)) {
            PathHelper.deleteRecursiveRetry(refDbRoot);
        }

        ObjectReferenceDatabase orefdb = new ObjectReferenceDatabase(refDbRoot, new ActivityReporter.Null());
        Map<String, BHive> hivesById = new TreeMap<>();
        bhives.forEach(h -> hivesById.put(h.getUri().toString(), h));

        log.info("Begin re-organization of {} hives using {}", bhives.size(), pool);

        try (ActionHandle org = as.start(new Action(Actions.REORGANIZE_POOL, null, null, pool.toString()),
                ActionExecution.fromSystem())) {
            // very low level as we are the only ones allowed to *write* to the pool
            ObjectDatabase poolDb = new ObjectDatabase(pool, pool.resolve("tmp"), new ActivityReporter.Null(), null);

            for (var entry : hivesById.entrySet()) {
                String id = entry.getKey();
                BHive hive = entry.getValue();
                if (ZipHelper.isZipUri(hive.getUri())) {
                    log.warn("Ignoring ZIP hive in pool reorganization: {}", id);
                    continue;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Finding references in {}", id);
                }

                // fetch *all* required objects regardless of origin. ignore missing in case a manifest was deleted after listing it.
                var hiveObjects = hive.execute(new ObjectListOperation().addManifest(hive.execute(new ManifestListOperation()))
                        .ignoreMissingManifest(true));

                // now mark all objects that remain as referenced. we mark *all*, also the duplicates
                hiveObjects.forEach(o -> orefdb.addReference(o, id));

                if (log.isDebugEnabled()) {
                    log.debug("Marked {} referenced objects in {}", hiveObjects.size(), id);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Reading all referenced objects");
            }

            log.info("Moving with threshold {}", usageThreshold);
            LongAdder count = new LongAdder();
            Map<String, List<ObjectId>> toRemove = new TreeMap<>();

            // walk through all referenced objects, move to pool as required, and remove from the origins in batches.
            Consumer<ObjectId> processOne = id -> {
                SortedSet<String> references = orefdb.read(id);
                boolean isPooled = poolDb.hasObject(id);

                if (references.size() >= usageThreshold || isPooled) {
                    List<BHive> refHives = references.stream().map(hivesById::get).collect(Collectors.toList());

                    // if reference count > usage threshold and not yet in pool -> move
                    if (!isPooled) {
                        moveToPool(poolDb, id, refHives);
                    }

                    // however it came to the pool, we need to prune it from all hives, remember the id.
                    references.forEach(r -> toRemove.computeIfAbsent(r, k -> new ArrayList<>()).add(id));
                    count.increment();
                }

                // every 10000 objects we perform the prune on the hives just to save on memory.
                long current = count.sum();
                if (current > 0 && current % PRUNE_LOOP_COUNT == 0) {
                    removeFromHives(hivesById, current, toRemove);
                }
            };

            // walk through all objects, processing them one by one.
            orefdb.walkAllObjects(processOne);

            // final batch of removals.
            if (!toRemove.isEmpty()) {
                removeFromHives(hivesById, count.sum(), toRemove);
            }

            log.info("Checked or moved {} referenced objects to the pool", count.sum());

            // cleanup of pool. look at all objects in pool - if it does not exist in the orefdb -> remove
            // pruning the pool is way easier than pruning a bhive! we do not need to care about concurrency.
            LongAdder remCount = new LongAdder();
            Consumer<ObjectId> remover = id -> {
                if (!orefdb.hasObject(id)) {
                    poolDb.removeObject(id);

                    if (log.isDebugEnabled()) {
                        log.debug("Removed {} from pool", id);
                    }

                    remCount.increment();
                }
            };
            poolDb.walkAllObjects(remover);
            if (log.isDebugEnabled()) {
                log.debug("Removed {} objects from the pool during cleanup", remCount.sum());
            }
        } catch (Exception e) {
            log.error("Cannot reorganize pool {}", pool, e);
        } finally {
            // delete the temporary reference counter db.
            PathHelper.deleteRecursiveRetry(refDbRoot);
        }

        log.info("Finished re-organization.");
    }

    private void removeFromHives(Map<String, BHive> hivesById, long current, Map<String, List<ObjectId>> toRemove) {
        if (log.isTraceEnabled()) {
            log.trace("...{}", current);
        }

        for (var entry : toRemove.entrySet()) {
            if (log.isTraceEnabled()) {
                log.trace("Assuring absence of {} objects from {}, already in pool", entry.getValue().size(), entry.getKey());
            }
            hivesById.get(entry.getKey()).execute(new InternalRemoveObjectsOperation().setObjectIds(entry.getValue()));
        }
        toRemove.clear();
    }

    private void moveToPool(ObjectDatabase poolDb, ObjectId id, List<BHive> origins) {
        // try to find a valid file.
        for (BHive origin : origins) {
            Path source = origin.execute(new InternalFindValidSourceOperation().setObjectId(id));
            if (source == null) {
                continue;
            }

            try {
                // first copy to pool, then remove from origins
                ObjectId calculated = poolDb.addObject(source);

                if (!id.equals(calculated)) {
                    // uh oh. this *should* never happen as we found a valid matching file in the origin.
                    throw new IllegalStateException("Cannot move " + id + " to pool, calculated " + calculated);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot move " + id, e);
            }

            // now remove from the origins.
            origins.forEach(h -> h.execute(new InternalRemoveObjectsOperation().setObjectIds(Collections.singletonList(id))));

            if (log.isDebugEnabled()) {
                log.debug("Moved {} to pool", id);
            }

            // done.
            return;
        }

        // if we got here no hive had a valid source for the object -> bad!
        throw new IllegalStateException("No origin BHive was able to supply a valid object for " + id);
    }

    /**
     * Convenience method which figures out common pool directories over a given set of hives and reorganizes each pool.
     *
     * @param registry the registry with all BHives to be examined.
     * @param usageThreshold the usageThreshold for objects to be considered for pooling.
     */
    public static void reorganizeAll(BHiveRegistry registry, int usageThreshold, ActionService as) {
        Map<Path, List<BHive>> pools = new TreeMap<>();

        for (var entry : registry.getAll().entrySet()) {
            BHive hive = entry.getValue();
            if (hive.isPooling() && hive.getPoolPath() != null) {
                pools.computeIfAbsent(hive.getPoolPath(), k -> new ArrayList<>()).add(hive);
            }
        }

        for (var entry : pools.entrySet()) {
            log.info("Re-organizing pool {} using {} BHives and threshold {}", entry.getKey(), entry.getValue().size(),
                    usageThreshold);
            new BHivePoolOrganizer(entry.getKey(), usageThreshold, as).reorganize(entry.getValue());
        }
    }

    /**
     * @param hive the hive with enabled pooling
     * @param target the target {@link ObjectDatabase} which should be populated with all required objects.
     */
    public static void unpoolHive(BHive hive, ObjectDatabase target) {
        if (!hive.isPooling()) {
            return; // nothing to do.
        }

        log.info("Begin unpooling {}", hive.getUri());

        // fetch *all* required objects regardless of origin. ignore missing in case a manifest was deleted after listing it.
        var reqObjects = hive.execute(
                new ObjectListOperation().addManifest(hive.execute(new ManifestListOperation())).ignoreMissingManifest(true));

        log.info("Moving and checking {} objects", reqObjects.size());

        // copy every single one of the objects to the target - it will avoid copy on its own if it already has the object.
        hive.execute(new InternalCopyObjectsOperation().setTarget(target).setObjectIds(reqObjects));
    }

    private static final class InternalFindValidSourceOperation extends Operation<Path> {

        private ObjectId oid;

        @Override
        public Path call() {
            if (oid == null) {
                throw new IllegalArgumentException("oid must be set");
            }

            Boolean valid = getObjectManager().db(db -> db.checkObject(oid));
            if (!Boolean.TRUE.equals(valid)) {
                return null;
            }

            return getObjectManager().db(db -> db.getObjectFile(oid));
        }

        public InternalFindValidSourceOperation setObjectId(ObjectId id) {
            this.oid = id;
            return this;
        }

    }

    private static final class InternalRemoveObjectsOperation extends Operation<Void> {

        private Collection<ObjectId> oid;

        @Override
        public Void call() {
            List<Future<?>> ops = new ArrayList<>();

            getObjectManager().db(db -> {
                oid.forEach(o -> ops.add(submitFileOperation(() -> {
                    try {
                        db.removeObject(o);
                    } catch (Exception e) {
                        // cannot remove, maybe in use? ignore the issue as the next re-org will try again.
                        if (log.isDebugEnabled()) {
                            log.debug("Cannot remove " + o, e);
                        }
                    }
                })));
                return null;
            });

            FutureHelper.awaitAll(ops);
            return null;
        }

        public InternalRemoveObjectsOperation setObjectIds(Collection<ObjectId> ids) {
            this.oid = ids;
            return this;
        }

    }

    private static final class InternalCopyObjectsOperation extends Operation<Void> {

        private Collection<ObjectId> oid;
        private ObjectDatabase target;

        @Override
        public Void call() {
            List<Future<?>> ops = new ArrayList<>();

            getObjectManager().db(db -> {
                oid.forEach(o -> ops.add(submitFileOperation(() -> {
                    try {
                        target.addObject(db.getObjectFile(o));
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot copy object " + o, e);
                    }
                })));
                return null;
            });

            FutureHelper.awaitAll(ops);
            return null;
        }

        public InternalCopyObjectsOperation setObjectIds(Collection<ObjectId> ids) {
            this.oid = ids;
            return this;
        }

        public InternalCopyObjectsOperation setTarget(ObjectDatabase db) {
            this.target = db;
            return this;
        }

    }

}

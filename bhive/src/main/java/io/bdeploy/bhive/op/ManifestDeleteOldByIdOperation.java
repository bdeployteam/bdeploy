package io.bdeploy.bhive.op;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Operation to delete manifests from the {@link ManifestDatabase} of
 * the {@link BHive} which follow the simple version counting scheme.
 */
public class ManifestDeleteOldByIdOperation extends BHive.Operation<Void> {

    private static final Logger log = LoggerFactory.getLogger(ManifestDeleteOldByIdOperation.class);

    private String manifestName;
    private int amountToKeep = 10;
    private boolean runGc;

    private Consumer<Key> preDelete;

    @Override
    public Void call() throws Exception {
        SortedSet<Key> execute = execute(new ManifestListOperation().setManifestName(manifestName));
        SortedMap<Long, List<Key>> mfsByKey = execute.stream()
                .collect(Collectors.groupingBy(k -> Long.valueOf(k.getTag()), TreeMap::new, Collectors.toList()));

        if (mfsByKey.size() <= amountToKeep) {
            return null;
        }

        long i = (mfsByKey.size() - amountToKeep);
        try (Activity activity = getActivityReporter().start("Deleting manifests...", i)) {
            for (Map.Entry<Long, List<Key>> entry : mfsByKey.entrySet()) {
                if (entry.getValue().size() != 1) {
                    log.warn("Expected exactly one manifest match per tag: {}, {}", entry.getKey(), entry.getValue());
                    continue;
                }
                Manifest.Key k = entry.getValue().get(0);
                if (preDelete != null) {
                    preDelete.accept(k);
                }
                execute(new ManifestDeleteOperation().setToDelete(k));
                if (--i == 0) {
                    break;
                }
                activity.workAndCancelIfRequested(1);
            }

            if (runGc) {
                execute(new PruneOperation());
            }
        }
        return null;
    }

    /**
     * Set the {@link Manifest} to be deleted.
     */
    public ManifestDeleteOldByIdOperation setToDelete(String toDelete) {
        this.manifestName = toDelete;
        return this;
    }

    /**
     * Set the amount of old versions to keep - defaults to 10.
     */
    public ManifestDeleteOldByIdOperation setAmountToKeep(int amount) {
        this.amountToKeep = amount;
        return this;
    }

    /**
     * Whether to execute the garbage collector right after deletion.
     */
    public ManifestDeleteOldByIdOperation setRunGarbageCollector(boolean gc) {
        this.runGc = gc;
        return this;
    }

    public ManifestDeleteOldByIdOperation setPreDeleteHook(Consumer<Manifest.Key> hook) {
        this.preDelete = hook;
        return this;
    }

}

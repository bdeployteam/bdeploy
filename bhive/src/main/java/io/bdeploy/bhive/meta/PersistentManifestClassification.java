package io.bdeploy.bhive.meta;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;

/**
 * @param <T> the classification type which is stored per manifest key
 */
public class PersistentManifestClassification<T> {

    private static final String CLASSIFICATION_PREFIX = ".classification/";
    private static final String CLASSIFICATION_FILE = "classification";

    private static final Logger log = LoggerFactory.getLogger(PersistentManifestClassification.class);

    private final String classificationName;
    private final Function<Manifest, T> classifier;
    private final BHive hive;

    private static final Map<String, Object> loadLocks = new TreeMap<>();

    private SortedMap<Manifest.Key, T> classifications;

    public PersistentManifestClassification(BHive hive, String name, Function<Manifest, T> classifier) {
        this.classificationName = CLASSIFICATION_PREFIX + "persistent/" + name;
        this.classifier = classifier;
        this.hive = hive;

        // we need a single static lock per classification name. it's ok to *never* expire this.
        loadLocks.computeIfAbsent(classificationName, k -> new Object());
    }

    /**
     * Loads an existing persistent classification and classifies all given {@link Key}s, potentially requiring to load
     * {@link Manifest}s to be able to do so if no information is stored (yet).
     * <p>
     * Callers need to take care that the given set of {@link Key}s represents the logically same set. Any {@link Key} in the
     * stored classification which is no longer in the given set will be removed from the persistent classification.
     *
     * @param keys the keys to classify.
     */
    @SuppressWarnings("unchecked")
    public void loadAndUpdate(Set<Manifest.Key> keys) {
        synchronized (loadLocks.get(classificationName)) {
            Optional<Long> id = hive.execute(new ManifestMaxIdOperation().setManifestName(classificationName));
            Manifest.Key key;

            if (!id.isPresent()) {
                classifications = new TreeMap<>();
            } else {
                key = new Manifest.Key(classificationName, id.get().toString());
                Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));

                try (InputStream is = hive
                        .execute(new TreeEntryLoadOperation().setRootTree(mf.getRoot()).setRelativePath(CLASSIFICATION_FILE))) {
                    classifications = StorageHelper.fromStream(is, CfStorage.class).storage;
                } catch (Exception e) {
                    log.debug("Cannot read {} for {}", CLASSIFICATION_FILE, key, e);
                    classifications = new TreeMap<>();
                }
            }

            boolean modified = false;

            // we *never* want to classify any classification manifests - ever.
            Set<Manifest.Key> filtered = keys.stream().filter(k -> !k.getName().startsWith(CLASSIFICATION_PREFIX))
                    .collect(Collectors.toSet());

            long sz = classifications.size();

            // first clean out the stored list of all keys not in the list.
            classifications = classifications.entrySet().stream().filter(e -> filtered.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
            modified = classifications.size() != sz;

            // now use classifier to classify whatever is not yet in the list.
            sz = classifications.size();
            try {
                filtered.stream().filter(k -> !classifications.containsKey(k))
                        .map(k -> hive.execute(new ManifestLoadOperation().setManifest(k))).forEach(m -> {
                            T classification = classifier.apply(m);
                            if (classification != null) {
                                classifications.put(m.getKey(), classification);
                            }
                        });

                // in case we found something to add, we mark modified and store back.
                modified = classifications.size() != sz;
            } catch (IllegalStateException ise) {
                // this can happen if *any* manifest is removed asynchronously so we cannot load it.
                if (log.isDebugEnabled()) {
                    log.debug("Manifest cannot be loaded while classification", ise);
                }
            }

            // finally store back the result if required.
            if (modified) {
                store();
            }
        }
    }

    /**
     * Stores the current classification, replacing the existing one. Storing is only performed if {@link #loadAndUpdate(Set)}
     * required a modification of the persistent classification as it was stored previously.
     */
    private void store() {
        // calculate target tag
        String targetTag = "1";
        Optional<Long> id = hive.execute(new ManifestMaxIdOperation().setManifestName(classificationName));
        if (id.isPresent()) {
            targetTag = Long.toString(id.get() + 1);
        }

        // calculate target key and builders.
        Manifest.Key targetKey = new Manifest.Key(classificationName, targetTag);
        Manifest.Builder newMf = new Manifest.Builder(targetKey);
        Tree.Builder newTree = new Tree.Builder();

        // actually store changes
        try (Transaction tx = hive.getTransactions().begin()) {
            // store classifications in BLOB
            CfStorage<T> storage = new CfStorage<>();
            storage.storage = classifications;
            ObjectId oid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(storage)));
            newTree.add(new Tree.Key(CLASSIFICATION_FILE, EntryType.BLOB), oid);

            // insert tree and manifest
            ObjectId newTreeId = hive.execute(new InsertArtificialTreeOperation().setTree(newTree));
            hive.execute(new InsertManifestOperation().addManifest(newMf.setRoot(newTreeId).build(hive)));
        }

        // cleanup old versions of the classifications.
        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(2).setToDelete(classificationName));
    }

    /**
     * Remove all existing classifications from disk.
     */
    public void remove() {
        // remove them all
        hive.execute(new ManifestDeleteOldByIdOperation().setAmountToKeep(0).setToDelete(classificationName));
    }

    public SortedMap<Manifest.Key, T> getClassifications() {
        return classifications;
    }

    public T getClassification(Manifest.Key key) {
        return classifications.get(key);
    }

    private static final class CfStorage<T> {

        @JsonTypeInfo(include = As.PROPERTY, property = "@class", use = Id.CLASS)
        public SortedMap<Manifest.Key, T> storage;

    }

}

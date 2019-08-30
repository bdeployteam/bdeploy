package io.bdeploy.interfaces.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ManifestRefLoadOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;

/**
 * Stores and reads instances from/to manifests.
 */
public class InstanceManifest {

    private static final Logger log = LoggerFactory.getLogger(InstanceManifest.class);

    public static final String INSTANCE_LABEL = "X-Instance";

    /**
     * The name of the node that is used to store client applications
     */
    public static final String CLIENT_NODE_NAME = "__ClientApplications";

    private final InstanceConfiguration config;
    private final SortedMap<String, Manifest.Key> nodes = new TreeMap<>();
    private final Manifest.Key key;

    private InstanceManifest(Manifest.Key key, InstanceConfiguration config) {
        this.key = key;
        this.config = config;
    }

    public static String getRootName(String uuid) {
        return uuid + "/root";
    }

    /**
     * Loads the manifest for the given instance from the given hive.
     *
     * @param hive
     *            hive containing the instance
     * @param instance
     *            ID of the instance
     * @param versionTag
     *            optional version tag. {@code null} for the latest version
     * @return the loaded manifest
     */
    public static InstanceManifest load(BHive hive, String instance, String versionTag) {
        Optional<Long> tag = Optional.empty();
        if (versionTag == null) {
            tag = hive.execute(new ManifestMaxIdOperation().setManifestName(getRootName(instance)));
        } else {
            try {
                tag = Optional.of(Long.parseLong(versionTag));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (!tag.isPresent()) {
            throw new WebApplicationException("Instance not found: " + instance, Status.NOT_FOUND);
        }
        return InstanceManifest.of(hive, new Manifest.Key(getRootName(instance), String.valueOf(tag.get())));
    }

    public static InstanceManifest of(BHive hive, Manifest.Key key) {
        Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));

        if (mf == null) {
            return null;
        }

        InstanceConfiguration ic;
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRelativePath(InstanceConfiguration.FILE_NAME)
                .setRootTree(hive.execute(new ManifestLoadOperation().setManifest(key)).getRoot()))) {
            ic = StorageHelper.fromStream(is, InstanceConfiguration.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load instance configuration " + InstanceConfiguration.FILE_NAME + " from " + key, e);
        } catch (IllegalStateException e) {
            return null; // in case this is not an instance manifest.
        }

        // don't actually load the product - it's optional on the minions.
        // this is to not force ALL of a product suite to the remote. minions may receive
        // only a subset of a product to deploy.
        InstanceManifest result = new InstanceManifest(key, ic);

        Tree root = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        for (Entry<Key, ObjectId> entry : root.getChildren().entrySet()) {
            // only manifest refs to minion config is allowed here.
            if (entry.getKey().getType() != Tree.EntryType.MANIFEST) {
                if (!entry.getKey().getName().equals(InstanceConfiguration.FILE_NAME)) {
                    log.warn("Unsupported file in instance manifest: {}", entry.getKey());
                }
                continue;
            }

            SortedMap<ObjectId, Manifest.Key> loaded = hive
                    .execute(new ManifestRefLoadOperation().addManifestRef(entry.getValue()));

            RuntimeAssert.assertTrue(loaded.size() == 1, "Cannot uniquely identify minion manifest for " + entry.getKey());

            result.nodes.put(entry.getKey().getName(), loaded.get(loaded.firstKey()));
        }

        return result;
    }

    /**
     * Find the application with the given ID in this configuration.
     *
     * @param hive the hive where the manifest is stored
     * @param applicationId unique name of the application
     */
    public ApplicationConfiguration getApplicationConfiguration(BHive hive, String applicationId) {
        for (Map.Entry<String, Manifest.Key> entry : getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (app.uid.equals(applicationId)) {
                    return app;
                }
            }
        }
        return null;
    }

    /**
     * @return the underlying configuration object.
     */
    public InstanceConfiguration getConfiguration() {
        return config;
    }

    /**
     * @return the {@link io.bdeploy.bhive.model.Manifest.Key key} of the underlying {@link Manifest}.
     */
    public Manifest.Key getManifest() {
        return key;
    }

    /**
     * Returns the name of the node along with a reference to the actual {@linkplain InstanceNodeManifest manifest}.
     *
     * @return a set containing the name of the node (=key) and its manifest reference (=value)
     */
    public SortedMap<String, Manifest.Key> getInstanceNodeManifests() {
        return nodes;
    }

    /**
     * @param hive the {@link BHive} to scan for available {@link InstanceManifest}s.
     * @return a {@link SortedSet} with all available {@link InstanceManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive, boolean onlyLatest) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        SortedSet<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());

        // for each manifest key find only the newest...
        Set<String> names = allKeys.stream().map(Manifest.Key::getName).distinct().collect(Collectors.toSet());
        SortedSet<Manifest.Key> idKeys = new TreeSet<>();

        if (onlyLatest) {
            for (String name : names) {
                Optional<Long> id = hive.execute(new ManifestMaxIdOperation().setManifestName(name));
                if (id.isPresent()) {
                    idKeys.add(new Manifest.Key(name, id.get().toString()));
                }
            }
        } else {
            idKeys.addAll(allKeys);
        }

        for (Manifest.Key key : idKeys) {
            Manifest mf = hive.execute(new ManifestLoadOperation().setManifest(key));
            if (mf.getLabels().containsKey(INSTANCE_LABEL)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Retrieve the history of this {@link InstanceManifest}.
     *
     * @param bhive the {@link BHiveExecution} used to perform operations on the history.
     * @return the {@link InstanceManifestHistory}
     */
    public InstanceManifestHistory getHistory(BHiveExecution bhive) {
        return new InstanceManifestHistory(getManifest(), bhive);
    }

    public static class Builder {

        private final SortedMap<String, Manifest.Key> instanceNodeManifests = new TreeMap<>();
        private InstanceConfiguration config;
        private Manifest.Key key;

        public Builder setInstanceConfiguration(InstanceConfiguration config) {
            this.config = config;
            return this;
        }

        public Builder addInstanceNodeManifest(String name, Manifest.Key ref) {
            instanceNodeManifests.put(name, ref);
            return this;
        }

        public Builder setKey(Manifest.Key key) {
            this.key = key;
            return this;
        }

        public Manifest.Key insert(BHive hive) {
            RuntimeAssert.assertNotNull(config.name, "Missing description");
            RuntimeAssert.assertNotNull(config.uuid, "Missing uuid");
            RuntimeAssert.assertNotNull(config.product, "Missing product");
            RuntimeAssert.assertNotNull(config.target, "Missing target");

            if (key == null) {
                String name = getRootName(config.uuid);
                Long next = hive.execute(new ManifestNextIdOperation().setManifestName(name));
                key = new Manifest.Key(name, next.toString());
            }

            Tree.Builder root = new Tree.Builder();

            // insert instance config
            root.add(new Tree.Key(InstanceConfiguration.FILE_NAME, Tree.EntryType.BLOB),
                    hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config))));

            // insert and record manifest reference
            instanceNodeManifests.forEach((k, v) -> root.add(new Tree.Key(k, Tree.EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(v))));

            Manifest.Builder mb = new Manifest.Builder(key)
                    .setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(root)))
                    .addLabel(INSTANCE_LABEL, config.uuid);

            hive.execute(new InsertManifestOperation().addManifest(mb.build(hive)));
            new InstanceManifestHistory(key, hive).record(Action.CREATE);

            return key;
        }

    }

}

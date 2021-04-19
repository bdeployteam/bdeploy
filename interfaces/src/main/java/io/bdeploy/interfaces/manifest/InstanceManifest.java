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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.Key;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
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
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributes;
import io.bdeploy.interfaces.manifest.banner.InstanceBanner;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.statistics.ClientUsage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * Stores and reads instances from/to manifests.
 */
public class InstanceManifest {

    private static final Logger log = LoggerFactory.getLogger(InstanceManifest.class);
    private static final String ROOT_SUFFIX = "/root";
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
        return uuid + ROOT_SUFFIX;
    }

    /**
     * Loads the manifest for the given instance from the given hive.
     *
     * @param hive hive containing the instance
     * @param instance ID of the instance
     * @param versionTag optional version tag. {@code null} for the latest version
     * @return the loaded manifest
     */
    public static InstanceManifest load(BHive hive, String instance, String versionTag) {
        Optional<Long> tag = Optional.empty();
        if (versionTag == null || versionTag.isEmpty()) {
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
        // this is to not force ALL of a product suite to the remote. minions may
        // receive
        // only a subset of a product to deploy.
        InstanceManifest result = new InstanceManifest(key, ic);

        Tree root = hive.execute(new TreeLoadOperation().setTree(mf.getRoot()));
        for (Entry<Key, ObjectId> entry : root.getChildren().entrySet()) {
            // only manifest refs to minion config is allowed here.
            if (entry.getKey().getType() != Tree.EntryType.MANIFEST) {
                if (!entry.getKey().getName().equals(InstanceConfiguration.FILE_NAME)
                        && !entry.getKey().getName().contentEquals("config")) {
                    log.warn("Unsupported entry in instance manifest: {}", entry.getKey());
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
     * @param hive the {@link BHive}
     * @param instanceVersion the version to delete, including instance node
     *            manifests.
     */
    public static void delete(BHive hive, Manifest.Key instanceVersion) {
        InstanceManifest im = of(hive, instanceVersion);
        if (im == null) {
            return;
        }

        // delete nodes
        for (Manifest.Key node : im.getInstanceNodeManifests().values()) {
            hive.execute(new ManifestDeleteOperation().setToDelete(node));
        }

        // delete version
        hive.execute(new ManifestDeleteOperation().setToDelete(instanceVersion));
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
     * Find the instance node with the given ID in this configuration.
     *
     * @param hive the hive where the manifest is stored
     * @param applicationId unique name of the application
     */
    public InstanceNodeConfiguration getInstanceNodeConfiguration(BHive hive, String applicationId) {
        for (Map.Entry<String, Manifest.Key> entry : getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            InstanceNodeConfiguration inc = inmf.getConfiguration();
            for (ApplicationConfiguration app : inc.applications) {
                if (app.uid.equals(applicationId)) {
                    return inc;
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
     * @return the {@link io.bdeploy.bhive.model.Manifest.Key key} of the underlying
     *         {@link Manifest}.
     */
    public Manifest.Key getManifest() {
        return key;
    }

    /**
     * Returns the name of the node along with a reference to the actual
     * {@linkplain InstanceNodeManifest manifest}.
     *
     * @return a set containing the name of the node (=key) and its manifest
     *         reference (=value)
     */
    public SortedMap<String, Manifest.Key> getInstanceNodeManifests() {
        return nodes;
    }

    /**
     * Returns the name of the node along with the actual node configuration.
     *
     * @param hive the hive where the manifest is stored
     * @return a set containing the name of the node (=key) and its manifest
     *         reference (=value)
     */
    public Map<String, InstanceNodeConfiguration> getInstanceNodeConfiguration(BHive hive) {
        Map<String, InstanceNodeConfiguration> result = new TreeMap<>();
        for (Map.Entry<String, Manifest.Key> entry : getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest imf = InstanceNodeManifest.of(hive, entry.getValue());
            result.put(entry.getKey(), imf.getConfiguration());
        }
        return result;
    }

    /**
     * @param hive the {@link BHive} to scan for available
     *            {@link InstanceManifest}s.
     * @return a {@link SortedSet} with all available {@link InstanceManifest}s.
     */
    public static SortedSet<Manifest.Key> scan(BHive hive, boolean onlyLatest) {
        SortedSet<Manifest.Key> result = new TreeSet<>();
        Set<Manifest.Key> allKeys = hive.execute(new ManifestListOperation());

        // find all manifest which look like an instance root manifest (ending in /root).
        Set<String> names = allKeys.stream().map(Manifest.Key::getName).distinct().filter(p -> p.endsWith(ROOT_SUFFIX))
                .collect(Collectors.toSet());
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
     * @param bhive the {@link BHiveExecution} used to perform operations on the
     *            history.
     * @return the {@link InstanceManifestHistory} which can be used to manipulate
     *         and query history.
     */
    public InstanceManifestHistory getHistory(BHiveExecution bhive) {
        return new InstanceManifestHistory(getManifest(), bhive);
    }

    /**
     * @param bhive the {@link BHiveExecution} used to perform operations on the
     *            state.
     * @return the {@link InstanceState} which can be use to manipulate and query
     *         state.
     */
    public InstanceState getState(BHiveExecution bhive) {
        return new InstanceState(getManifest(), bhive);
    }

    /**
     * @param bhive the {@link BHiveExecution} used to perform operations on the
     *            banner.
     * @return the {@link InstanceBanner} which can be use to set or remove the
     *         banner.
     */
    public InstanceBanner getBanner(BHiveExecution bhive) {
        return new InstanceBanner(getManifest(), bhive);
    }

    /**
     * @param bhive the {@link BHiveExecution} used to perform operations on the
     *            usage statistics.
     * @return the {@link ClientUsage} which can be use to set or remove the client
     *         usage statistics.
     */
    public ClientUsage getClientUsage(BHiveExecution bhive) {
        return new ClientUsage(getManifest(), bhive);
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
            try (Transaction t = hive.getTransactions().begin()) {
                return doInsertLocked(hive);
            }
        }

        private Manifest.Key doInsertLocked(BHive hive) {
            RuntimeAssert.assertNotNull(config.name, "Missing description");
            RuntimeAssert.assertNotNull(config.uuid, "Missing uuid");
            RuntimeAssert.assertNotNull(config.product, "Missing product");

            if (key == null) {
                String name = getRootName(config.uuid);
                Long next = hive.execute(new ManifestNextIdOperation().setManifestName(name));
                key = new Manifest.Key(name, next.toString());
            }

            Tree.Builder root = new Tree.Builder();

            // insert instance config
            root.add(new Tree.Key(InstanceConfiguration.FILE_NAME, Tree.EntryType.BLOB),
                    hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(config))));

            // reference config tree
            if (config.configTree != null) {
                root.add(new Tree.Key("config", Tree.EntryType.TREE), config.configTree);
            }

            // insert and record manifest reference
            instanceNodeManifests.forEach((k, v) -> root.add(new Tree.Key(k, Tree.EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(v))));

            Manifest.Builder mb = new Manifest.Builder(key)
                    .setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(root)))
                    .addLabel(INSTANCE_LABEL, config.uuid);

            hive.execute(new InsertManifestOperation().addManifest(mb.build(hive)));
            return key;
        }

    }

    public CustomAttributes getAttributes(BHiveExecution hive) {
        return new CustomAttributes(getManifest(), hive);
    }

}

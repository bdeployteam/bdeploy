package io.bdeploy.ui.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.plugin.VersionSorterService;

class CleanupInstanceGroupContext {

    private final String group;
    private final BHive hive;
    private final VersionSorterService vss;
    private final InstanceGroupConfiguration instanceGroupConfiguration;

    private SortedSet<Key> allProductManifests;
    private Map<String, List<Key>> allProductsMap;
    private SortedSet<Key> allInstanceManifests;
    private SortedSet<Key> latestInstanceManifests;
    private Map<String, Comparator<Key>> comparators;

    private final Map<String, SortedSet<Key>> instanceVersions4Uninstall = new HashMap<>();
    private final SortedSet<Manifest.Key> allManifests4deletion = new TreeSet<>();

    public CleanupInstanceGroupContext(String group, BHive hive, VersionSorterService vss) {
        this.group = group;
        this.hive = hive;
        this.vss = vss;
        instanceGroupConfiguration = new InstanceGroupManifest(hive).read();
    }

    public String getGroup() {
        return group;
    }

    public BHive getHive() {
        return hive;
    }

    public InstanceGroupConfiguration getInstanceGroupConfiguration() {
        return instanceGroupConfiguration;
    }

    public SortedSet<Key> getAllProductManifests() {
        if (allProductManifests == null) {
            allProductManifests = ProductManifest.scan(hive);
        }
        return allProductManifests;
    }

    private Map<String, List<Key>> getAllProductsMap() {
        if (allProductsMap == null) {
            allProductsMap = getAllProductManifests().stream().collect(Collectors.groupingBy(Key::getName,
                    Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), l -> {
                        Collections.sort(l, (a, b) -> getComparator(a).compare(b, a));
                        return l;
                    })));
        }
        return allProductsMap;
    }

    public Set<String> getAllProductNames() {
        return getAllProductsMap().keySet();
    }

    public List<Key> getAllProductVersions(String productName) {
        return getAllProductsMap().get(productName);
    }

    public SortedSet<Key> getAllInstanceManifests() {
        if (allInstanceManifests == null) {
            allInstanceManifests = InstanceManifest.scan(hive, false);
        }
        return allInstanceManifests;
    }

    public SortedSet<Key> getLatestInstanceManifests() {
        if (latestInstanceManifests == null) {
            latestInstanceManifests = InstanceManifest.scan(hive, true);
        }
        return latestInstanceManifests;
    }

    public Comparator<Key> getComparator(Key product) {
        if (comparators == null) {
            comparators = new TreeMap<>();
        }
        return comparators.computeIfAbsent(product.getName(), k -> vss.getKeyComparator(group, product));
    }

    public void addInstanceVersions(String name, SortedSet<Key> keys) {
        instanceVersions4Uninstall.put(name, keys);
        allManifests4deletion.addAll(keys);
    }

    public SortedSet<Key> getInstanceVersions4Uninstall(String imName) {
        return instanceVersions4Uninstall.get(imName);
    }

    public SortedSet<Manifest.Key> getAllManifests4deletion() {
        return allManifests4deletion;
    }

    public void addManifest4deletion(Manifest.Key key) {
        this.allManifests4deletion.add(key);
    }

}

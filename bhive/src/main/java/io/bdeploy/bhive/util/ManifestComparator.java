package io.bdeploy.bhive.util;

import java.util.Comparator;

import io.bdeploy.bhive.model.Manifest.Key;

/**
 * Comparator for manifest keys that are using a simple numeric version counting schema.
 */
public class ManifestComparator {

    public static final Comparator<Key> NEWEST_FIRST = (a, b) -> Long.compare(Long.parseLong(b.getTag()),
            Long.parseLong(a.getTag()));

    private ManifestComparator() {
    }

}
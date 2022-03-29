package io.bdeploy.bhive.model;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.Manifest.ReferenceKey;

public class SortManifestsByReferences implements Comparator<Manifest> {

    @Override
    public int compare(Manifest o1, Manifest o2) {
        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null || o2 == null) {
            return o1 == null ? -1 : 1;
        }

        // cache not yet *available* - this is different from *empty*
        if (o1.internalGetCachedReferences() == null && o2.internalGetCachedReferences() == null) {
            return o1.getKey().compareTo(o2.getKey());
        }
        if (o1.internalGetCachedReferences() == null) {
            return -1;
        }
        if (o2.internalGetCachedReferences() == null) {
            return 1;
        }

        Set<Key> o1r = o1.internalGetCachedReferences().stream().map(ReferenceKey::getKey).collect(Collectors.toSet());
        Set<Key> o2r = o2.internalGetCachedReferences().stream().map(ReferenceKey::getKey).collect(Collectors.toSet());

        // empty cache means that we *know* that there are no references at all.
        if (o1r.isEmpty() && o2r.isEmpty()) {
            return o1.getKey().compareTo(o2.getKey());
        }
        if (o1r.isEmpty()) {
            return -1;
        }
        if (o2r.isEmpty()) {
            return 1;
        }

        // both not empty.
        if (o1r.contains(o2.getKey()) && o2r.contains(o1.getKey())) {
            throw new IllegalStateException("Circular manifest reference found: " + o1.getKey() + " <-> " + o2.getKey());
        }
        if (o1r.contains(o2.getKey())) {
            // o1 has a reference to o2, so o1 is smaller
            return -1;
        }
        if (o2r.contains(o1.getKey())) {
            // o2 has a reference to o1, so o1 is greater
            return 1;
        }

        // no relation between the two, compare key
        return o1.getKey().compareTo(o2.getKey());
    }

}

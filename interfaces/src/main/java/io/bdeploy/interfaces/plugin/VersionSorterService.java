package io.bdeploy.interfaces.plugin;

import java.util.Comparator;

import io.bdeploy.bhive.model.Manifest;

public interface VersionSorterService {

    /**
     * Returns a comparator which sorts by name lexically and by tag descending, using a possibly product specific mechanism.
     *
     * @param group the instance group or <code>null</code> to retrieve the default scheme.
     * @param product the product for which keys are compared. Any key of the product is valid. Pass <code>null</code> to retrieve
     *            the default scheme.
     * @return a {@link Comparator} which can compare keys of the given product.
     */
    public Comparator<Manifest.Key> getKeyComparator(String group, Manifest.Key product);

    /**
     * Creates a comparator which can compare tags for the given product, using a possibly product specific plugin to interpret
     * the tag as version. Versions are sorted ascending (newest version last).
     *
     * @param group the instance group or <code>null</code> to retrieve the default scheme.
     * @param product the product to compare tags for or <code>null</code> to retrieve the default scheme.
     * @return the {@link Comparator}.
     */
    public Comparator<String> getTagComparator(String group, Manifest.Key product);

}

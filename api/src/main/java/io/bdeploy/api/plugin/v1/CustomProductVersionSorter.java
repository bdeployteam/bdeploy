package io.bdeploy.api.plugin.v1;

import java.util.Comparator;

/**
 * Provides a custom sorting algorithm for product versions.
 * <p>
 * {@link CustomProductVersionSorter} in <b>global</b> plugins are ignored. Only per-product plugins are allowed to contribute
 * <b>one</b> {@link CustomProductVersionSorter}. If a product ships multiple plugins with a {@link CustomProductVersionSorter},
 * the behavior is <b>undefined</b> (i.e. one of the versions is used).
 * <p>
 * The {@link CustomProductVersionSorter} implementation is always taken from the plugin with the highest version number. This is
 * independent of the product version number, as the {@link CustomProductVersionSorter} is the one determining the highest version
 * number.
 */
public class CustomProductVersionSorter {

    private final Comparator<String> sorter;

    public CustomProductVersionSorter(Comparator<String> sorter) {
        this.sorter = sorter;
    }

    /**
     * @return an instance of a Java {@link Comparator} which is used on the server side for sorting. This field is only available
     *         on the server which loaded the plugin.
     */
    public Comparator<String> getSorter() {
        return sorter;
    }

}

package io.bdeploy.common.util;

import java.util.Comparator;

/**
 * A comparator that ensures that a given string is put at the end. All others are sorted lexicographically.
 */
public class SortOneAsLastComparator implements Comparator<String> {

    private final String name;

    /**
     * Creates a new comparator that sorts the given string as last one
     */
    public SortOneAsLastComparator(String masterName) {
        this.name = masterName;
    }

    @Override
    public int compare(String a, String b) {
        if (name.equals(a)) {
            return 1;
        } else if (name.equals(b)) {
            return -1;
        }
        return a.compareTo(b);
    }

}

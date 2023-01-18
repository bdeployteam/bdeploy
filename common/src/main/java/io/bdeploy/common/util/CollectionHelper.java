package io.bdeploy.common.util;

import java.util.List;
import java.util.function.Predicate;

public class CollectionHelper {

    private CollectionHelper() {
        // static helper.
    }

    /**
     * Searches for the index of an element in the list by using a predicated.
     */
    public static <T> int indexOf(List<T> list, Predicate<T> match) {
        for (int i = 0; i < list.size(); ++i) {
            if (match.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

}

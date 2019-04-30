package io.bdeploy.common.util;

/**
 * Helper for {@link java.lang.String}
 */
public class StringHelper {

    private StringHelper() {
    }

    /**
     * Checks whether a given string is null or empty
     *
     * @param s
     *            string to check
     * @return <code>true</code> if the string is null or empty
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

}

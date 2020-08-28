package io.bdeploy.common.util;

/**
 * Helper for {@link java.lang.String}
 */
public class StringHelper {

    private StringHelper() {
    }

    /**
     * Returns whether the given string is null or empty
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Returns whether the given strings are equal
     */
    public static boolean notEqual(String a, String b) {
        return a != null && !a.equals(b);
    }

    /**
     * Removes all line breaks from the given string
     */
    public static String removeLineBreaks(String s) {
        return s.replace("\n", "").replace("\r", "");
    }

    /**
     * Checks if the string contains only lowercase characters.
     */
    public static boolean isAllLowerCase(String s) {
        if (s.trim().isEmpty()) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLowerCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}

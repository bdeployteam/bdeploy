package io.bdeploy.common.util;

/**
 * Helper for {@link java.lang.String}
 */
public class StringHelper {

    private StringHelper() {
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

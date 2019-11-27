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

}

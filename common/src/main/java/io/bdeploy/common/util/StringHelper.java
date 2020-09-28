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
     * Returns whether the given strings are NOT equal
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

    /**
     * Returns a string whose value is the concatenation of this
     * string repeated {@code count} times.
     * <p>
     * If this string is empty or count is zero then the empty
     * string is returned.
     * <p>
     * This is less efficient than the Java 11 implementation as we
     * cannot reuse the original Strings encoding to fill arrays.
     *
     * @param count number of times to repeat
     * @return A string composed of this string repeated
     *         {@code count} times or the empty string if this
     *         string is empty or count is zero
     * @throws IllegalArgumentException if the {@code count} is
     *             negative.
     */
    public static String repeat(String string, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 1) {
            return string;
        }
        byte[] value = string.getBytes();
        final int len = value.length;
        if (len == 0 || count == 0) {
            return "";
        }
        if (Integer.MAX_VALUE / count < len) {
            throw new OutOfMemoryError(
                    "Repeating " + len + " bytes String " + count + " times will produce a String exceeding maximum size.");
        }

        StringBuilder builder = new StringBuilder(string.length() * count);

        while (count-- > 0) {
            builder.append(string);
        }

        return builder.toString();
    }

}

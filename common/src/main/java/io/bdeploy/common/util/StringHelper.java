package io.bdeploy.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    /**
     * Returns a string whose value is the concatenation of this
     * string repeated {@code count} times.
     * <p>
     * If this string is empty or count is zero then the empty
     * string is returned.
     * <p>
     * NOTE: Borrowed from the Java 11 implementation in {@link String}.
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
        if (len == 1) {
            final byte[] single = new byte[count];
            Arrays.fill(single, value[0]);
            return new String(single, StandardCharsets.UTF_8);
        }
        if (Integer.MAX_VALUE / count < len) {
            throw new OutOfMemoryError(
                    "Repeating " + len + " bytes String " + count + " times will produce a String exceeding maximum size.");
        }
        final int limit = len * count;
        final byte[] multiple = new byte[limit];
        System.arraycopy(value, 0, multiple, 0, len);
        int copied = len;
        for (; copied < limit - copied; copied <<= 1) {
            System.arraycopy(multiple, 0, multiple, copied, copied);
        }
        System.arraycopy(multiple, 0, multiple, copied, limit - copied);
        return new String(multiple, StandardCharsets.UTF_8);
    }

}

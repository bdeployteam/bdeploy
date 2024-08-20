package io.bdeploy.common.util;

import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Calculates new UUIDs which can be used to uniquely name artifacts.
 */
public class UuidHelper {

    public static final Pattern UUID_PATTERN = Pattern.compile("[a-z0-9]{4}-[a-z0-9]{3}-[a-z0-9]{4}");

    private static final String AB = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final Random RND = new SecureRandom();

    private UuidHelper() {
    }

    /**
     * @return a pseudo-UUID generated from random data.
     */
    public static String randomId() {
        StringBuilder builder = new StringBuilder();

        // This *must* match the pattern in UUID_PATTERN above.
        builder.append(randomString(4)).append("-").append(randomString(3)).append("-").append(randomString(4));
        return builder.toString();
    }

    private static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(RND.nextInt(AB.length())));
        }
        return sb.toString();
    }

}

package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.bdeploy.common.Version;

public class VersionHelper {

    private static final String VERSION_PROP = "version";
    private static final Pattern V_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)([-\\.].*)*");
    public static final Version UNDEFINED = VersionHelper.parse("0.0.0");

    private static final Version version;
    private static final Properties properties = readProperties();
    static {
        if (!properties.containsKey(VERSION_PROP)) {
            properties.put(VERSION_PROP, System.getProperty("bdeploy.version.override", "0.0.0"));
        }
        version = parse(properties.getProperty(VERSION_PROP));
    }

    private VersionHelper() {
    }

    public static Version getVersion() {
        return version;
    }

    public static Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    private static Properties readProperties() {
        try (InputStream is = VersionHelper.class.getResourceAsStream("/version.properties")) {
            Properties p = new Properties();
            p.load(is);

            return p;
        } catch (IOException | RuntimeException e) {
            return new Properties();
        }
    }

    /**
     * Parses and compares the two version object. An exception is thrown if one of the strings does not represent a valid
     * version.
     *
     * @param a
     *            first version to compare
     * @param b
     *            second version to compare
     * @return a negative integer, zero, or a positive integer as this version is less than, equal to, or greater than the
     *         specified version.
     */
    public static int compare(String a, String b) {
        return compare(VersionHelper.parse(a), VersionHelper.parse(b));
    }

    /**
     * Compares the two versions taking {@code null} into account. If both versions are {@code null} then they are assumed
     * to be equal. Otherwise {@code null} is treated as lower than any other version.
     *
     * @param a
     *            first version to compare
     * @param b
     *            second version to compare
     * @return a negative integer, zero, or a positive integer as this version is less than, equal to, or greater than the
     *         specified version.
     */
    public static int compare(Version a, Version b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /**
     * Compares whether or not the two versions are equal.
     */
    public static boolean equals(Version a, Version b) {
        return compare(a, b) == 0;
    }

    /**
     * Parses the given string into a version. Throws an exception if parsing fails.
     *
     * @param v the string to parse
     * @return the version object. Never {@code null}
     */
    public static Version parse(String v) {
        Version version = tryParse(v);
        if (version == null) {
            throw new IllegalArgumentException("Given version does not match expected pattern");
        }
        return version;
    }

    /**
     * Tries to parse the given string into a version object.
     *
     * @param v the string to parse
     * @return the version object or {@code null} in case that the string is not a version
     */
    public static Version tryParse(String v) {
        Matcher matcher = V_PATTERN.matcher(v);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), matcher.group(4));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Returns whether or not the currently running version is undefined. Typically happens when starting the minion in the
     * development environment.
     */
    public static boolean isRunningUndefined() {
        return isUndefined(getVersion());
    }

    /**
     * Returns whether or not the given version represents the undefined version.
     */
    public static boolean isUndefined(Version version) {
        return equals(version, UNDEFINED);
    }

}

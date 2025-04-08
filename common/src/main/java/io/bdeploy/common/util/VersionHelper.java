package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.Version;

public class VersionHelper {

    private static final Logger log = LoggerFactory.getLogger(VersionHelper.class);

    private static final String VERSION_PROP = "version";
    private static final Pattern V_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)([-\\.].*)*");
    public static final Version UNDEFINED = VersionHelper.parse("0.0.0");

    private static final Version CURRENT_VERSION;
    private static final Properties properties = readProperties();
    static {
        if (!properties.containsKey(VERSION_PROP)) {
            properties.put(VERSION_PROP, System.getProperty("bdeploy.version.override", "0.0.0"));
        }
        CURRENT_VERSION = parse(properties.getProperty(VERSION_PROP));
    }

    private VersionHelper() {
    }

    public static Version getVersion() {
        return CURRENT_VERSION;
    }

    public static String getVersionAsString() {
        if (isRunningUndefined()) {
            return "undefined";
        }
        return CURRENT_VERSION.toString();
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
            if (log.isTraceEnabled()) {
                log.trace("Failed to read properties", e);
            }
            return new Properties();
        }
    }

    /**
     * Parses and compares the two version {@link String Strings}. An exception is thrown if one of the strings does not represent
     * a valid version.
     *
     * @param a A {@link String} containing the first version to compare
     * @param b A {@link String} containing the second version to compare
     * @return A negative integer, zero, or a positive integer as the first version is older than, equal to, or newer than the
     *         second version
     * @see #parse(String)
     * @see #compare(Version, Version)
     */
    public static int compare(String a, String b) {
        return compare(VersionHelper.parse(a), VersionHelper.parse(b));
    }

    /**
     * Compares two {@link Version Versions}. If both versions are {@code null} then they are assumed to be equal. Otherwise
     * {@code null} is treated as older than any other version.
     *
     * @param a The first {@link Version} to compare
     * @param b The second {@link Version} to compare
     * @return A negative integer, zero, or a positive integer as the first {@link Version} is older than, equal to, or newer than
     *         the second {@link Version}
     * @see #compare(String, String)
     */
    public static int compare(Version a, Version b) {
        if (a == b) {
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
     * Parses the given {@link String} into a {@link Version}.
     *
     * @param v The {@link String} to parse
     * @return The {@link Version} object, never {@code null}
     * @throws IllegalArgumentException If the given {@link String} cannot be parsed to a {@link Version}
     */
    public static Version parse(String v) {
        Version version = tryParse(v);
        if (version == null) {
            throw new IllegalArgumentException("Given version does not match expected pattern");
        }
        return version;
    }

    /**
     * Tries to parse the given {@link String} into a {@link Version} object.
     *
     * @param v The {@link String} to parse
     * @return The {@link Version} object or {@code null} if the parsing failed
     */
    public static Version tryParse(String v) {
        if (v == null) {
            return null;
        }
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

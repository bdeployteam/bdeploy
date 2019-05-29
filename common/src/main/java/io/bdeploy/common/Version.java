package io.bdeploy.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

/**
 * A Version in the format <major>.<minor>.<micro>[-qualifier]|[.qualifier]
 */
public class Version implements Comparable<Version> {

    public static final Pattern V_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)([-\\.].*)*");

    private final int major;
    private final int minor;
    private final int micro;
    private final String qualifier;

    public Version(int major, int minor, int micro, String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.qualifier = qualifier;
    }

    public static Version parse(String v) {
        Matcher matcher = V_PATTERN.matcher(v);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Given version does not match expected pattern");
        }

        return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), matcher.group(4));
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getMicro() {
        return micro;
    }

    public String getQualifier() {
        return qualifier;
    }

    @Override
    public int compareTo(Version o) {
        return ComparisonChain.start().compare(major, o.major).compare(minor, o.minor).compare(micro, o.micro)
                .compare(qualifier, o.qualifier, Ordering.natural().nullsFirst()).result();
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + micro + (qualifier == null ? "" : qualifier);
    }

}

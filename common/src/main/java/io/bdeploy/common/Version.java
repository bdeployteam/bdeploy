package io.bdeploy.common;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

/**
 * A Version in the format &ltmajor&gt.&ltminor&gt.&ltmicro&gt[-qualifier]|[.qualifier]
 */
public class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int micro;
    private final String qualifier;

    @JsonCreator
    public Version(@JsonProperty("major") int major, @JsonProperty("minor") int minor, @JsonProperty("micro") int micro,
            @JsonProperty("qualifier") String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.qualifier = qualifier;
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
        // nulls last: this means that a release version is newer than a snapshot.
        return ComparisonChain.start().compare(major, o.major).compare(minor, o.minor).compare(micro, o.micro)
                .compare(qualifier, o.qualifier, Ordering.natural().nullsLast()).result();
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + micro + (qualifier == null ? "" : qualifier);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + major;
        result = prime * result + micro;
        result = prime * result + minor;
        result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Version other = (Version) obj;
        if (major != other.major) {
            return false;
        }
        if (micro != other.micro) {
            return false;
        }
        if (minor != other.minor) {
            return false;
        }
        if (qualifier == null) {
            if (other.qualifier != null) {
                return false;
            }
        } else if (!qualifier.equals(other.qualifier)) {
            return false;
        }
        return true;
    }

}

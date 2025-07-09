package io.bdeploy.api.product.v1.impl;

import javax.annotation.Generated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * A {@link ScopedManifestKey} provides additional scope to the BHive "name" and "tag" concept by mangling additional scoping
 * information into the manifest name.
 */
public class ScopedManifestKey {

    private static final Logger log = LoggerFactory.getLogger(ScopedManifestKey.class);

    private final String name;
    private final OperatingSystem os;
    private final String tag;

    public ScopedManifestKey(String name, OperatingSystem os, String tag) {
        this.name = name;
        this.os = os;
        this.tag = tag;
    }

    /**
     * @param key a {@link Key} in a format understandable as {@link ScopedManifestKey}.
     * @return a {@link ScopedManifestKey}, or <code>null</code> if the key could not be parsed
     */
    public static ScopedManifestKey parse(Manifest.Key key) {
        String name = key.getName();
        String tag = key.getTag();

        int lastSlash = name.lastIndexOf('/');
        try {
            return new ScopedManifestKey(name.substring(0, lastSlash),
                    OperatingSystem.valueOf(name.substring(lastSlash + 1).toUpperCase()), tag);
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("Failed to parse key: {}", key, e);
            }
            return null;
        }
    }

    /**
     * @param key a string representation of a {@link Key} <b>without</b> the operating system part of the key.
     * @param os the {@link OperatingSystem} to set for the {@link ScopedManifestKey}.
     * @return a {@link ScopedManifestKey}.
     */
    public static ScopedManifestKey parse(String key, OperatingSystem os) {
        Manifest.Key k = Manifest.Key.parse(key);
        return new ScopedManifestKey(k.getName(), os, k.getTag());
    }

    /**
     * @param spec the original name
     * @param os the {@link OperatingSystem} for which to create a name
     * @return the fully qualified name based on original name and os.
     */
    public static String createScopedName(String spec, OperatingSystem os) {
        return spec + '/' + os.name().toLowerCase();
    }

    public Manifest.Key getKey() {
        return new Manifest.Key(createScopedName(name, os), tag);
    }

    public OperatingSystem getOperatingSystem() {
        return os;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return getKey().toString();
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((os == null) ? 0 : os.hashCode());
        result = prime * result + ((tag == null) ? 0 : tag.hashCode());
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
        ScopedManifestKey other = (ScopedManifestKey) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (os != other.os) {
            return false;
        }
        if (tag == null) {
            if (other.tag != null) {
                return false;
            }
        } else if (!tag.equals(other.tag)) {
            return false;
        }
        return true;
    }

}

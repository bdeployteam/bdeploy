package io.bdeploy.interfaces;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * A {@link ScopedManifestKey} provides additional scope to the BHive "name" and "tag" concept by mangling additional scoping
 * information into the manifest name.
 */
public class ScopedManifestKey {

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
     * @return a {@link ScopedManifestKey}
     */
    public static ScopedManifestKey parse(Manifest.Key key) {
        String name = key.getName();
        String tag = key.getTag();

        int lastSlash = name.lastIndexOf('/');
        try {
            return new ScopedManifestKey(name.substring(0, lastSlash),
                    OperatingSystem.valueOf(name.substring(lastSlash + 1).toUpperCase()), tag);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param key a string representation of a {@link Key}, including tag.
     * @return a {@link ScopedManifestKey}
     */
    public static ScopedManifestKey parse(String key) {
        return parse(Manifest.Key.parse(key));
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

    public boolean isSameBase(ScopedManifestKey other) {
        return name.equals(other.name) && tag.equals(other.tag);
    }

}

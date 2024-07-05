package io.bdeploy.interfaces.variables;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.model.Manifest;

/**
 * Provides the absolute installation path as a String for a given Manifest.
 */
public class ManifestRefPathProvider {

    private final Map<Manifest.Key, Path> paths;

    public ManifestRefPathProvider(Map<Manifest.Key, Path> paths) {
        this.paths = paths;
    }

    /**
     * Scan all available {@link Manifest}s in the export directory for the managed
     * deployment for a {@link Manifest} with the given name, without regards to the
     * tag.
     *
     * @param name the name of the {@link Manifest} to lookup
     * @return the absolute {@link Path} to the {@link Manifest} installation
     *         directory
     */
    public Path getManifestPath(String name) {
        if (name.contains(":")) {
            Manifest.Key fullKey = Manifest.Key.parse(name);
            return paths.get(fullKey);
        }

        List<Path> candidates = new ArrayList<>();

        // check OS specific
        for (Map.Entry<Manifest.Key, Path> entry : paths.entrySet()) {
            ScopedManifestKey smk = ScopedManifestKey.parse(entry.getKey());
            if (smk != null && smk.getName().equals(name)) {
                candidates.add(entry.getValue());
            }
        }

        // only resolve non-OS specific if no OS specific match has been found.
        if (candidates.isEmpty()) {
            for (Map.Entry<Manifest.Key, Path> entry : paths.entrySet()) {
                if (entry.getKey().getName().equals(name)) {
                    candidates.add(entry.getValue());
                }
            }
        }

        assertFalse(candidates.size() > 1, "Ambigous Manifest name lookup: " + name + ", candidates: " + candidates);
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(0);
    }
}

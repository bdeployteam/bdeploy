package io.bdeploy.interfaces.variables;

import static io.bdeploy.common.util.RuntimeAssert.assertFalse;
import static io.bdeploy.common.util.RuntimeAssert.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * Provides the absolute installation path as a String for a given Manifest.
 */
public class ManifestRefPathProvider {

    private final Path exportDir;
    private final SortedMap<String, Manifest.Key> relPaths;

    public ManifestRefPathProvider(DeploymentPathProvider provider, SortedMap<String, Manifest.Key> relPaths) {
        this.exportDir = provider.get(SpecialDirectory.BIN);
        this.relPaths = relPaths;

        assertTrue(exportDir.isAbsolute(), "Given directory is not absolute: " + exportDir);
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
            return relPaths.entrySet().stream().filter(e -> e.getValue().equals(fullKey)).map(e -> exportDir.resolve(e.getKey()))
                    .findFirst().orElse(null);
        }

        List<Path> candidates = new ArrayList<>();
        for (Map.Entry<String, Manifest.Key> entry : relPaths.entrySet()) {
            if (entry.getValue().getName().equals(name)) {
                candidates.add(exportDir.resolve(entry.getKey()));
            }
        }

        // only resolve OS specific if no direct match has been found.
        if (candidates.isEmpty()) {
            // check OS specific
            for (Map.Entry<String, Manifest.Key> entry : relPaths.entrySet()) {
                ScopedManifestKey smk = ScopedManifestKey.parse(entry.getValue());
                if (smk != null) {
                    if (smk.getName().equals(name)) {
                        candidates.add(exportDir.resolve(entry.getKey()));
                    }
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

package io.bdeploy.bhive.remote.jersey;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.ManifestSpawnListener;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * The {@link BHive} registry holds all instances of {@link BHive} which are served by the server.
 * <p>
 * The registry can hold {@link BHive} which are manually registered as well as {@link BHive}s discovered when scanning a
 * registered
 * location.
 */
public class BHiveRegistry implements AutoCloseable {

    /**
     * A listener which can be attached to the registry which is notified whenever {@link Manifest}s spawn in any of the
     * registered hives, see {@link ManifestSpawnListener}.
     */
    public interface MultiManifestSpawnListener {

        public void spawn(String hiveName, Collection<Manifest.Key> keys);
    }

    private final Set<Path> locations = new TreeSet<>();
    private final Map<String, BHive> hives = new TreeMap<>();
    private final ActivityReporter reporter;
    private final Function<BHive, Permission> permissionClassifier;
    private final List<MultiManifestSpawnListener> listeners = new ArrayList<>();
    private final Map<String, ManifestSpawnListener> internalListeners = new TreeMap<>();

    /**
     * @param reporter the {@link ActivityReporter} used for all {@link BHive} discovered by the registry
     * @param permissionClassifier a classifier which determines the required access permission per BHive. It is allowed to return
     *            <code>null</code> (no permission required).
     */
    public BHiveRegistry(ActivityReporter reporter, Function<BHive, Permission> permissionClassifier) {
        this.reporter = reporter;
        this.permissionClassifier = permissionClassifier;
    }

    public Permission getRequiredPermission(BHive hive) {
        if (permissionClassifier == null) {
            // default is to require read permission on a hive.
            return Permission.READ;
        }

        return permissionClassifier.apply(hive);
    }

    /**
     * @param listener a listener to be notified if {@link Manifest}s spawn in any of the registered {@link BHive}s.
     */
    public void addManifestSpawnListener(MultiManifestSpawnListener listener) {
        listeners.add(listener);
    }

    /**
     * @param listener a previously registered {@link MultiManifestSpawnListener}.
     */
    public void removeManifestSpawnListener(MultiManifestSpawnListener listener) {
        listeners.remove(listener);
    }

    /**
     * Manually register an additional (non-discovered) {@link BHive} to be available in the registry.
     * <p>
     * Note that this {@link BHive} will be {@link BHive#close() closed} along with all other {@link BHive} when the registry is
     * destroyed.
     */
    public void register(String name, BHive hive) {
        hives.put(name, hive);

        // redirecting listener
        ManifestSpawnListener listener = keys -> listeners.forEach(l -> l.spawn(name, keys));

        internalListeners.put(name, listener);
        hive.addSpawnListener(listener);
    }

    /**
     * Removes this hive from the registry and closes all open resources
     *
     * @param name the hive to remove.
     */
    public void unregister(String name) {
        BHive hive = hives.remove(name);
        RuntimeAssert.assertNotNull(hive);

        hive.removeSpawnListener(internalListeners.remove(name));
        hive.close();
    }

    /**
     * Retrieve a named {@link BHive}.
     */
    public BHive get(String name) {
        return hives.get(name);
    }

    /**
     * @return all registered {@link BHive}s
     */
    public Map<String, BHive> getAll() {
        return hives;
    }

    /**
     * @return all locations which have been scanned for {@link BHive}s
     */
    public Set<Path> getLocations() {
        return locations;
    }

    /**
     * @param location the location to scan for available {@link BHive}s (recursively).
     */
    public void scanLocation(Path location, Function<Path, Auditor> auditorFactory) {
        locations.add(location);

        // find hives and register.
        HiveFinder finder = new HiveFinder();
        try {
            Files.walkFileTree(location, finder);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan location: " + location, e);
        }
        finder.hives.forEach(
                dir -> register(dir.getFileName().toString(), new BHive(dir.toUri(), auditorFactory.apply(dir), reporter)));
    }

    /**
     * @return the HK2 {@link Binder} which can be used to provide the {@link BHiveRegistry} using dependency
     *         injection.
     */
    public Binder binder() {
        return new AbstractBinder() {

            @Override
            protected void configure() {
                bind(BHiveRegistry.this).to(BHiveRegistry.class);
            }
        };
    }

    @Override
    public void close() {
        hives.values().forEach(BHive::close);
        hives.clear();
    }

    /**
     * Identifies a {@link BHive} during a recursive location scan.
     */
    private static final class HiveFinder extends SimpleFileVisitor<Path> {

        List<Path> hives = new ArrayList<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path o = dir.resolve("objects");
            Path m = dir.resolve("manifests");

            // is a hive, remember
            if (Files.isDirectory(o) && Files.isDirectory(m)) {
                hives.add(dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            // continue the search
            return FileVisitResult.CONTINUE;
        }
    }

}

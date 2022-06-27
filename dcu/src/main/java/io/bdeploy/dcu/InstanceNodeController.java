package io.bdeploy.dcu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationPoolType;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DelayedVariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.EnvironmentVariableResolver;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.ManifestVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;

/**
 * A {@link InstanceNodeController} is a unit which can be locally deployed.
 * <p>
 * It includes all required application {@link Manifest}s as well as all the
 * configuration required to install and run the applications.
 */
public class InstanceNodeController {

    private static final Logger log = LoggerFactory.getLogger(InstanceNodeController.class);

    /**
     * Name of the file stored in the runtime directory containing the serialized
     * process configuration
     */
    private static final String PCU_JSON = "pcu.json";

    private final BHive hive;
    private final TaskSynchronizer syncOps;
    private final InstanceNodeManifest manifest;
    private final CompositeResolver resolvers = new CompositeResolver();
    private final Path root;
    private final DeploymentPathProvider paths;

    /**
     * @param hive the hive to export artifacts from
     * @param root the root directory used for deployment/runtime
     * @param manifest the instance node manifest
     */
    public InstanceNodeController(BHive hive, Path root, InstanceNodeManifest manifest, TaskSynchronizer syncOps) {
        this.hive = hive;
        this.syncOps = syncOps;
        this.root = root;
        this.manifest = manifest;
        this.paths = new DeploymentPathProvider(root.resolve(manifest.getUUID()), manifest.getKey().getTag());

        // Setup default resolvers for this node
        InstanceNodeConfiguration config = manifest.getConfiguration();
        this.resolvers.add(new InstanceVariableResolver(config, paths, manifest.getKey().getTag()));
        this.resolvers.add(new DelayedVariableResolver(resolvers));
        this.resolvers.add(new OsVariableResolver());
        this.resolvers.add(new EnvironmentVariableResolver());
        this.resolvers.add(new DeploymentPathResolver(paths));
        this.resolvers.add(new ParameterValueResolver(new ApplicationParameterProvider(config)));
    }

    public InstanceNodeManifest getManifest() {
        return manifest;
    }

    /**
     * @param external an additional variable resolver. It is passed the name of a
     *            variable and expected to return a value if known, or
     *            <code>null</code> if not able to resolve the variable.
     */
    public void addAdditionalVariableResolver(VariableResolver external) {
        resolvers.add(external);
    }

    /**
     * Install this manifest. It will create (or assume) the following structure in
     * the given root:
     *
     * <pre>
     *  + root
     *  +-- {@literal <deployment-uuid>/data} (shared data directory - only created if missing)
     *  +-- {@literal <deployment-uuid>/deploy/<id-of-this-update>/<content-of-manifest>}
     *  +-- {@literal <deployment-uuid>/deploy/<id-of-this-update>/runtime/pcu.json} (PCU configuration)
     *  +-- {@literal <deployment-uuid>/deploy/<id-of-this-update>/runtime/*} (runtime dir (stdout log, ...) for each app).
     * </pre>
     *
     * The manifest is expected to have this content structure:
     *
     * <pre>
     *  + {@literal <content-of-manifest>}
     *  +-- config/* (configuration files as configured centrally)
     *  +-- manifests/* (manifestations of applications, additional manifests (e.g. JDK), ...)
     *  +-- deployment.json (information about processes, parameters, ...)
     * </pre>
     *
     * In case of any error during installation, the created directories and files
     * are cleaned up.
     *
     * @return the UUID of the just installed manifest.
     */
    public String install() {
        if (isInstalled()) {
            return manifest.getUUID();
        }
        try {
            installConfigurationTo(manifest.getConfiguration());
            return manifest.getUUID();
        } catch (Exception e) {
            PathHelper.deleteRecursive(paths.get(SpecialDirectory.BIN));
            throw e;
        }
    }

    public void uninstall() {
        if (!isInstalled()) {
            return;
        }
        Path dir = getDeploymentDir();
        PathHelper.deleteRecursive(dir);
    }

    /**
     * @return whether this manifest is already installed on disc.
     */
    public boolean isInstalled() {
        Path dir = getDeploymentDir();
        if (Files.exists(dir)) {
            // check if all required applications exist
            long missingApps = getManifest().getConfiguration().applications.stream().filter(a -> !isApplicationInstalled(a))
                    .count();
            if (missingApps > 0) {
                return false;
            }

            return Files.exists(paths.get(SpecialDirectory.RUNTIME).resolve(PCU_JSON));
        }
        return false;
    }

    private boolean isApplicationInstalled(ApplicationConfiguration config) {
        Path target;
        if (config.pooling == ApplicationPoolType.GLOBAL || config.pooling == null) {
            target = paths.get(SpecialDirectory.MANIFEST_POOL).resolve(config.application.directoryFriendlyName());
        } else if (config.pooling == ApplicationPoolType.LOCAL) {
            target = paths.get(SpecialDirectory.INSTANCE_MANIFEST_POOL).resolve(config.application.directoryFriendlyName());
        } else {
            target = paths.get(SpecialDirectory.BIN).resolve(config.application.directoryFriendlyName());
        }

        if (syncOps.isPerforming(target)) {
            return false;
        }

        return Files.isDirectory(target);
    }

    /**
     * Returns the directory where this instance is deployed to.
     */
    public Path getDeploymentDir() {
        return paths.get(SpecialDirectory.BIN);
    }

    /**
     * Returns the path provider for this instance node
     */
    public DeploymentPathProvider getDeploymentPathProvider() {
        return paths;
    }

    /**
     * Returns the resolver that is capable to resolve variables for the given
     * instance.
     */
    public VariableResolver getResolver() {
        return resolvers;
    }

    /**
     * Reads the persisted process group configuration from the file-system. Can
     * only be done if the node is fully deployed.
     */
    public ProcessGroupConfiguration getProcessGroupConfiguration() {
        Path deploymentRoot = root.resolve(manifest.getUUID());
        DeploymentPathProvider dpp = new DeploymentPathProvider(deploymentRoot, manifest.getKey().getTag());

        Path processConfigFile = dpp.getAndCreate(SpecialDirectory.RUNTIME).resolve(PCU_JSON);
        if (!Files.exists(processConfigFile)) {
            return null;
        }
        return StorageHelper.fromPath(processConfigFile, ProcessGroupConfiguration.class);
    }

    private void installConfigurationTo(InstanceNodeConfiguration dc) {
        Path targetDir = paths.get(SpecialDirectory.BIN);
        PathHelper.deleteRecursive(targetDir);

        // write root config tree to the according target location
        ObjectId rootTree = manifest.getConfigTrees().get(InstanceNodeManifest.ROOT_CONFIG_NAME);
        if (rootTree != null) {
            syncOps.perform(targetDir, () -> hive.execute(new ExportTreeOperation().setSourceTree(rootTree)
                    .setTargetPath(targetDir.resolve(SpecialDirectory.CONFIG.getDirName()))));
        }

        // write all required applications to the pool
        SortedMap<Manifest.Key, Path> exportedPaths = installPooledApplicationsFor(dc);

        // create a variable resolver which can expand all supported variables.
        resolvers.add(new ManifestVariableResolver(new ManifestRefPathProvider(paths, exportedPaths)));

        // render configuration files.
        processConfigurationTemplates(paths.get(SpecialDirectory.CONFIG), resolvers);

        // render the PCU information.
        ProcessGroupConfiguration processGroupConfig = dc.renderDescriptor(resolvers, dc);
        try {
            Files.write(paths.getAndCreate(SpecialDirectory.RUNTIME).resolve(PCU_JSON),
                    StorageHelper.toRawBytes(processGroupConfig), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write PCU information", e);
        }

        // make sure that the data directory always exists
        paths.getAndCreate(SpecialDirectory.DATA);
    }

    private SortedMap<Key, Path> installPooledApplicationsFor(InstanceNodeConfiguration dc) {
        Path poolRoot = paths.getAndCreate(SpecialDirectory.MANIFEST_POOL);
        Path instancePoolRoot = paths.getAndCreate(SpecialDirectory.INSTANCE_MANIFEST_POOL);
        Path noPoolRoot = paths.getAndCreate(SpecialDirectory.BIN);

        SortedMap<Key, Path> result = new TreeMap<>();

        LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
        SortedMap<Path, Set<Manifest.Key>> pools = new TreeMap<>();

        for (ApplicationConfiguration app : dc.applications) {
            if (app.pooling == null || app.pooling == ApplicationPoolType.GLOBAL) {
                pools.computeIfAbsent(poolRoot, k -> new TreeSet<>()).add(app.application);
            } else if (app.pooling == ApplicationPoolType.LOCAL) {
                pools.computeIfAbsent(instancePoolRoot, k -> new TreeSet<>()).add(app.application);
            } else if (app.pooling == ApplicationPoolType.NONE) {
                pools.computeIfAbsent(noPoolRoot, k -> new TreeSet<>()).add(app.application);
            }

            ApplicationManifest amf = ApplicationManifest.of(hive, app.application);

            // applications /must/ follow the ScopedManifestKey rules.
            ScopedManifestKey smk = ScopedManifestKey.parse(app.application);

            // the dependency must be here. it has been pushed here with the configuration.
            // all dependencies go to the global pool
            pools.computeIfAbsent(poolRoot, k -> new TreeSet<>())
                    .addAll(localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));
        }

        for (Map.Entry<Path, Set<Manifest.Key>> entry : pools.entrySet()) {
            for (Manifest.Key key : entry.getValue()) {
                Path target = entry.getKey().resolve(key.directoryFriendlyName());
                result.put(key, target);

                if (!Files.isDirectory(target)) {
                    syncOps.perform(target, () -> hive.execute(new ExportOperation().setTarget(target).setManifest(key)));
                }
            }
        }

        return result;
    }

    private void processConfigurationTemplates(Path path, VariableResolver resolver) {
        if (!Files.isDirectory(path)) {
            return; // nothing to do.
        }

        try (Stream<Path> allPaths = Files.walk(path)) {
            allPaths.filter(Files::isRegularFile).forEach(p -> processConfigurationFile(p, resolver));
        } catch (IOException e) {
            log.error("Cannot walk configuration file tree", e);
        }
    }

    private void processConfigurationFile(Path file, VariableResolver resolver) {
        try (InputStream check = Files.newInputStream(file)) {
            if (!StreamHelper.isTextFile(check)) {
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot check if file is a text file: " + file, e);
        }

        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String processed = TemplateHelper.process(content, resolver);
            Files.write(file, processed.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
        } catch (Exception e) {
            // might have missing variable references, since we only 'see' what is on our
            // node. Applications from other nodes are not available.
            log.warn("Cannot process configuration file: {}: {}", file, e.toString());
            if (log.isDebugEnabled()) {
                log.debug("Error details", e);
            }
        }
    }

    /**
     * Scans the given {@link BHive} and the given deployment root {@link Path} and
     * deletes any deployment from the deployment root {@link Path} which are no
     * longer available in the {@link BHive}.
     *
     * @param source the source {@link BHive} to scan for available
     *            {@link InstanceNodeController}s.
     * @param root the {@link Path} where all deployments reside.
     */
    public static List<CleanupAction> cleanup(BHive source, Path root, SortedSet<Manifest.Key> toBeRemoved) {
        List<CleanupAction> toRemove = new ArrayList<>();
        SortedSet<Manifest.Key> inHive = InstanceNodeManifest.scan(source);

        // Manifests which are marked for removal can safely be assumed to be gone.
        inHive.removeAll(toBeRemoved);

        // Only keep things which are actually installed, so check all manifests
        List<InstanceNodeController> toKeep = new ArrayList<>();
        for (Manifest.Key key : inHive) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(source, key);
            InstanceNodeController inc = new InstanceNodeController(source, root, inmf, new TaskSynchronizer());

            if (inc.isInstalled()) {
                toKeep.add(inc);
            }
        }

        log.info("Collecting garbage in instance node deployments...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root,
                p -> !p.getFileName().toString().equals(SpecialDirectory.MANIFEST_POOL.getDirName()))) {
            // name of each directory is an instance UUID.
            for (Path dir : stream) {
                cleanupBinDir(toRemove, toKeep, dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan deployment directories", e);
        }

        cleanupPoolDirs(source, root, toRemove, toKeep);

        return toRemove;
    }

    private static void cleanupPoolDirs(BHive source, Path root, List<CleanupAction> toRemove,
            List<InstanceNodeController> toKeep) {
        log.info("Collecting garbage in application pool...");
        OperatingSystem runningOs = OsHelper.getRunningOs();
        TreeSet<String> requiredKeys = new TreeSet<>();
        SortedMap<String, TreeSet<String>> requiredInstanceKeys = new TreeMap<>();
        for (InstanceNodeController inc : toKeep) {
            // add all applications and all of their dependencies as resolved locally.
            inc.getManifest().getConfiguration().applications.stream().peek(a -> {
                SortedSet<String> deps = ApplicationManifest.of(source, a.application).getDescriptor().runtimeDependencies;
                if (deps == null) {
                    return;
                }
                deps.stream().map(d -> LocalDependencyFetcher.resolveSingleLocal(source, d, runningOs))
                        .map(Manifest.Key::directoryFriendlyName).forEach(requiredKeys::add);
            }).forEach(a -> {
                if (a.pooling == ApplicationPoolType.GLOBAL || a.pooling == null) {
                    requiredKeys.add(a.application.directoryFriendlyName());
                } else if (a.pooling == ApplicationPoolType.LOCAL) {
                    requiredInstanceKeys.computeIfAbsent(inc.getManifest().getUUID(), k -> new TreeSet<>())
                            .add(a.application.directoryFriendlyName());
                } // type NONE is cleaned with the instance bin directory.
            });
        }

        // clean global pool
        Path poolDir = root.resolve(SpecialDirectory.MANIFEST_POOL.getDirName());
        cleanPoolDir(toRemove, requiredKeys, poolDir);

        // clean per-instance pool
        for (Map.Entry<String, TreeSet<String>> entry : requiredInstanceKeys.entrySet()) {
            Path instancePoolDir = root.resolve(entry.getKey()).resolve(SpecialDirectory.INSTANCE_MANIFEST_POOL.getDirName());
            cleanPoolDir(toRemove, entry.getValue(), instancePoolDir);
        }
    }

    private static void cleanPoolDir(List<CleanupAction> toRemove, TreeSet<String> requiredKeys, Path poolDir) {
        if (!Files.isDirectory(poolDir)) {
            return;
        }

        try (DirectoryStream<Path> poolStream = Files.newDirectoryStream(poolDir)) {
            for (Path pooled : poolStream) {
                if (!requiredKeys.contains(pooled.getFileName().toString())) {
                    toRemove.add(new CleanupAction(CleanupType.DELETE_FOLDER, pooled.toAbsolutePath().toString(),
                            "Remove stale pooled application"));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan pool directory", e);
        }
    }

    private static void cleanupBinDir(List<CleanupAction> toRemove, List<InstanceNodeController> toKeep, Path dir)
            throws IOException {
        Path binDir = dir.resolve(SpecialDirectory.BIN.getDirName());

        // if there is no bin-dir in there, remove it all together as spurious
        // directory.
        if (!Files.isDirectory(binDir)) {
            toRemove.add(new CleanupAction(CleanupType.DELETE_FOLDER, dir.toAbsolutePath().toString(),
                    "Remove spurious directory (no binary directory found)"));
            return;
        }

        boolean hasChild = false;
        try (DirectoryStream<Path> binStream = Files.newDirectoryStream(binDir)) {
            // each directory is a tag.
            for (Path tagPath : binStream) {
                Optional<String> anyMatch = findAliveTag(toKeep, dir, tagPath);
                if (anyMatch.isPresent()) {
                    hasChild = true; // keep parent directory alive.
                } else {
                    // delete AT LEAST this bin directory.
                    toRemove.add(new CleanupAction(CleanupType.DELETE_FOLDER, tagPath.toAbsolutePath().toString(),
                            "Delete stale binary folder"));
                }
            }
        }

        if (!hasChild) {
            // remove the whole deployment, no active version anymore. ATTENTION: deletes
            // logs, etc.
            toRemove.add(new CleanupAction(CleanupType.DELETE_FOLDER, dir.toAbsolutePath().toString(),
                    "Delete instance data (no more instance versions available)"));
        }
    }

    private static Optional<String> findAliveTag(List<InstanceNodeController> toKeep, Path uuidPath, Path tagPath) {
        return toKeep.stream().map(inc -> inc.getManifest().getKey())
                // filter for matching instance UUID.
                .filter(k -> k.getName().startsWith(uuidPath.getFileName().toString() + "/"))
                // map to the tags which are still present in the hive.
                .map(Manifest.Key::getTag)
                // filter for the tag we're looking for.
                .filter(t -> t.equals(tagPath.getFileName().toString()))
                // check if such an element exists (yes or no).
                .findAny();
    }

}

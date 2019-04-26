package io.bdeploy.dcu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.InstanceVariableResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.VariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A {@link InstanceNodeController} is a unit which can be locally deployed.
 * <p>
 * It includes all required application {@link Manifest}s as well as all the
 * configuration required to install and run the applications.
 */
public class InstanceNodeController {

    private static final Logger log = LoggerFactory.getLogger(InstanceNodeController.class);

    /** Name of the file stored in the runtime directory containing the serialized process configuration */
    private static final String PCU_JSON = "pcu.json";

    private final BHive hive;
    private final InstanceNodeManifest manifest;
    private final List<Function<String, String>> additionalResolvers = new ArrayList<>();
    private final Path root;
    private final DeploymentPathProvider paths;

    /**
     * @param hive the hive to export artifacts from
     * @param root the root directory used for deployment/runtime
     * @param manifest the instance node manifest
     */
    public InstanceNodeController(BHive hive, Path root, InstanceNodeManifest manifest) {
        this.hive = hive;
        this.root = root;
        this.manifest = manifest;
        this.paths = new DeploymentPathProvider(root.resolve(manifest.getUUID()), manifest.getKey().getTag());
        addAdditionalVariableResolver(new InstanceVariableResolver(manifest.getConfiguration()));
    }

    public InstanceNodeManifest getManifest() {
        return manifest;
    }

    /**
     * @param external an additional variable resolver. It is passed the name of a
     *            variable and expected to return a value if known, or
     *            <code>null</code> if not able to resolve the variable.
     */
    public void addAdditionalVariableResolver(Function<String, String> external) {
        additionalResolvers.add(external);
    }

    /**
     * Install this manifest. It will create (or assume) the following
     * structure in the given root:
     *
     * <pre>
     *  + root
     *  +-- &lt;deployment-uuid&gt;/data (shared data directory - only created if missing)
     *  +-- &lt;deployment-uuid&gt;/deploy/&lt;id-of-this-update&gt;/&lt;content-of-manifest&gt;
     *  +-- &lt;deployment-uuid&gt;/deploy/&lt;id-of-this-update&gt;/runtime/pcu.json (PCU configuration)
     *  +-- &lt;deployment-uuid&gt;/deploy/&lt;id-of-this-update&gt;/runtime/* (runtime dir (stdout log, ...) for each app).
     * </pre>
     *
     * The manifest is expected to have this content structure:
     *
     * <pre>
     *  + &lt;content-of-manifest&gt;
     *  +-- config/* (configuration files as configured centrally)
     *  +-- manifests/* (manifestations of applications, additional manifests (e.g. JDK), ...)
     *  +-- deployment.json (information about processes, parameters, ...)
     * </pre>
     *
     * In case of any error during installation, the created directories and files are
     * cleaned up.
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
            return true;
        }
        return false;
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
     * Reads the persisted process group configuration from the file-system. Can only be done if the node is fully deployed.
     */
    public ProcessGroupConfiguration getProcessGroupConfiguration() {
        Path deploymentRoot = root.resolve(manifest.getUUID());
        DeploymentPathProvider paths = new DeploymentPathProvider(deploymentRoot, manifest.getKey().getTag());

        Path processConfigFile = paths.getAndCreate(SpecialDirectory.RUNTIME).resolve(PCU_JSON);
        if (!Files.exists(processConfigFile)) {
            return null;
        }
        return StorageHelper.fromPath(processConfigFile, ProcessGroupConfiguration.class);
    }

    private void installConfigurationTo(InstanceNodeConfiguration dc) {
        // write all the manifest content to the according target location
        hive.execute(new ExportOperation().setManifest(manifest.getKey()).setTarget(paths.get(SpecialDirectory.BIN)));

        // find out which Manifest is deployed where in the tree.
        SortedMap<String, Manifest.Key> includedManifests = hive
                .execute(new ManifestRefScanOperation().setManifest(manifest.getKey()));

        // render the PCU information.
        VariableResolver resolver = new VariableResolver(paths, new ManifestRefPathProvider(paths, includedManifests),
                new ApplicationParameterProvider(dc), additionalResolvers);
        ProcessGroupConfiguration processGroupConfig = dc.renderDescriptor(resolver);

        try {
            Files.write(paths.getAndCreate(SpecialDirectory.RUNTIME).resolve(PCU_JSON),
                    StorageHelper.toRawBytes(processGroupConfig));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write PCU information", e);
        }
    }

    /**
     * Scans the given {@link BHive} and the given deployment root {@link Path} and deletes any deployment from the deployment
     * root
     * {@link Path} which are no longer available in the {@link BHive}.
     *
     * @param source the source {@link BHive} to scan for available {@link InstanceNodeController}s.
     * @param root the {@link Path} where all deployments reside.
     */
    public static void cleanup(BHive source, Path root) {
        SortedSet<Manifest.Key> inHive = InstanceNodeManifest.scan(source);
        SortedMap<ObjectId, List<Path>> onDisc = scan(root);

        SortedSet<ObjectId> availableRoots = inHive.stream().map(k -> source.execute(new ManifestLoadOperation().setManifest(k)))
                .filter(Objects::nonNull).map(Manifest::getRoot).collect(Collectors.toCollection(TreeSet::new));

        for (Map.Entry<ObjectId, List<Path>> candidate : onDisc.entrySet()) {
            if (availableRoots.contains(candidate.getKey())) {
                continue; // still available - OK
            }

            log.warn("Stale deployment(s) found: " + candidate.getValue());
            candidate.getValue().forEach(PathHelper::deleteRecursive);
        }
    }

    /**
     * @param root the root deployment directory to scan for on-disc deployments of {@link InstanceNodeController}s.
     * @return a set of found UUIDs which are deployed.
     */
    private static SortedMap<ObjectId, List<Path>> scan(Path root) {
        SortedMap<ObjectId, List<Path>> result = new TreeMap<>();
        try {
            Files.walk(root, 1).forEach(uuid -> {
                scanKeys(uuid, result);
            });
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list deployed UUIDs in " + root, e);
        }
    }

    /**
     * @param uuidRoot the UUID root directory within the deployment root
     * @param result a map to contribute to. places a mapping of root OID to actual deployment path.
     */
    private static void scanKeys(Path uuidRoot, SortedMap<ObjectId, List<Path>> result) {
        try {
            Files.walk(uuidRoot, 1).forEach(oid -> {
                // FIXME: this is WRONG now. filename is not the OID but the manifest tag
                ObjectId id = ObjectId.parse(oid.getFileName().toString());
                if (id != null) {
                    result.computeIfAbsent(id, x -> new ArrayList<>()).add(oid);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list deployed root trees in " + uuidRoot, e);
        }
    }

}

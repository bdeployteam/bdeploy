package io.bdeploy.interfaces;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.CommonUpdateResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Provides shared functionality for dealing with updates.
 */
public class UpdateHelper {

    public static final Version UPDATE_API_V1 = new Version(1, 0, 0, null);

    public static final Version SERVER_OLD_VERSION = new Version(1, 0, 0, null);
    public static final Version SERVER_LIMIT_VERSION = new Version(7, 7, 0, null);

    /**
     * Prefix for all update package {@link Key}s.
     */
    public static final String SW_META_PREFIX = "meta/";

    /**
     * Suffix for 'snapshot' update packages (non-release-builds).
     */
    public static final String SW_SNAPSHOT = "/snapshot";

    /**
     * Name of the 'launcher' software.
     */
    public static final String SW_LAUNCHER = "launcher";

    /**
     * Name of the 'bdeploy' software.
     */
    public static final String SW_BDEPLOY = "bdeploy";

    /**
     * Directory (within the update dir) where to put the "to-be-installed" software.
     */
    public static final String UPDATE_DIR = "next";

    /**
     * This must match what the launcher script(s) expect to perform an update.
     */
    public static final int CODE_RESTART = 42;

    private static final Logger log = LoggerFactory.getLogger(UpdateHelper.class);

    private UpdateHelper() {
    }

    public static boolean isBDeployServerKey(Manifest.Key key) {
        return key.getName().startsWith(SW_META_PREFIX + SW_BDEPLOY);
    }

    /**
     * @param updateDir the update directory root
     * @return the path to the update directory to use.
     */
    public static Path prepareUpdateDirectory(Path updateDir) {
        Path updateTarget = updateDir.resolve(UpdateHelper.UPDATE_DIR);
        if (Files.isDirectory(updateTarget)) {
            log.warn("Removing stale update folder at {}", updateTarget);
            PathHelper.deleteRecursiveRetry(updateTarget);
        }
        return updateTarget;
    }

    /**
     * @param updateZipFileOrDir the update ZIP file or the directory of the unpacked ZIP containing the actual software
     * @param tmpDir a (unique) directory where to place temporary files and directories (e.g. when unpacking the ZIP file). The
     *            directory is assumed to be deleted by the caller.
     * @param hive the hive to import into. may be (but does not have to be) located inside the temp directory.
     * @return the {@link Key} of the imported {@link Manifest}.
     * @throws IOException
     */
    public static List<Manifest.Key> importUpdate(Path updateZipFileOrDir, Path tmpDir, BHive hive) throws IOException {
        PathHelper.mkdirs(tmpDir);

        Manifest.Key key;
        Path updContent = null;
        if (Files.isDirectory(updateZipFileOrDir)) {
            Path vprops = updateZipFileOrDir.resolve("version.properties");
            if (!Files.exists(vprops)) {
                throw new IllegalStateException("Missing " + vprops);
            }

            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(vprops)) {
                props.load(is);
            }

            key = calculateKeyFromProperties(props, null);
            updContent = updateZipFileOrDir;
        } else {
            key = calculateKeyFromDistZip(updateZipFileOrDir);

            // extract ZIP to src dir
            Path zipDir = Files.createTempDirectory(tmpDir, "unzip-");
            ZipHelper.unzip(updateZipFileOrDir, zipDir);

            // expecting a single directory in the ZIP containing all the things.
            try (DirectoryStream<Path> content = Files.newDirectoryStream(zipDir)) {
                for (Path path : content) {
                    if (Files.isDirectory(path)) {
                        if (updContent != null) {
                            throw new IllegalStateException("More than one directory in update package");
                        }
                        updContent = path;
                    }
                }
            }
        }

        if (updContent == null) {
            throw new IllegalStateException("Cannot find update directory");
        }

        RuntimeAssert.assertTrue(Files.isDirectory(updContent), "Cannot find update content directory: " + updContent);

        List<Manifest.Key> result = new ArrayList<>();
        doImportUpdates(tmpDir, hive, result, key, updContent);
        return result;
    }

    private static void doImportUpdates(Path tmpDir, BHive hive, List<Manifest.Key> result, Manifest.Key key, Path updContent)
            throws IOException {
        // check for included launcher update packages and import as well.
        Path tmpLaunchers = null;
        Path launchers = updContent.resolve(SW_LAUNCHER);
        if (Files.exists(launchers)) {
            tmpLaunchers = Files.createTempDirectory(tmpDir, "tmp-");
            try (DirectoryStream<Path> nestedLaunchers = Files.newDirectoryStream(launchers, "launcher-*.zip")) {
                for (Path launcherZip : nestedLaunchers) {
                    log.info("Importing nested update: {}", launcherZip.getFileName());
                    try {
                        result.addAll(importUpdate(launcherZip, tmpDir, hive));
                    } catch (Exception e) {
                        log.error("Cannot import nested update package, see debug logs for more info: {}", launcherZip);
                        if (log.isDebugEnabled()) {
                            log.debug("Underlying Exception: ", e);
                        }
                    }

                    // move single files as /tmp might be on different file system, and directory move is not possible.
                    PathHelper.moveRetry(launcherZip, tmpLaunchers.resolve(launcherZip.getFileName()));
                }
            }

            // avoid launchers being part of the actual bdeploy manifest, temporarily move them.
        }

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setSourcePath(updContent).setManifest(key));
            result.add(key);
        } finally {
            // restore nested launcher zips for follow up inits or updates.
            if (tmpLaunchers != null) {
                try (DirectoryStream<Path> allTmpLaunchers = Files.newDirectoryStream(tmpLaunchers)) {
                    for (Path launcher : allTmpLaunchers) {
                        PathHelper.moveRetry(launcher, launchers.resolve(launcher.getFileName()));
                    }
                }
            }
        }
    }

    /**
     * Extract name and version from dist ZIP, as defined in gradle build.
     */
    private static Key calculateKeyFromDistZip(Path zip) {
        // find a file version.properties in the ZIP
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith("version.properties") && !entry.isDirectory()) {
                    Properties props = new Properties();
                    props.load(zipInputStream);

                    return calculateKeyFromProperties(props, null);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot unzip update package", e);
        }

        throw new IllegalStateException("Cannot calculate update key for " + zip);
    }

    private static Key calculateKeyFromProperties(Properties props, OperatingSystem os) {
        String name = SW_BDEPLOY;

        String project = props.getProperty("project");
        String version = props.getProperty("version");

        if (os == null) {
            os = OperatingSystem.valueOf(props.getProperty("os").toUpperCase());
        }

        if (SW_LAUNCHER.equals(project)) {
            name = project;
        }

        if (version == null) {
            throw new IllegalStateException("Cannot determine version for update package");
        }

        if (Boolean.parseBoolean(props.getProperty("snapshot"))) {
            name += SW_SNAPSHOT;
        }

        return new ScopedManifestKey(SW_META_PREFIX + name, os, version).getKey();
    }

    /**
     * Requests the master to install the given update packages.
     *
     * @param svc
     *            the remote service to install the updates
     * @param keys
     *            the update packages
     * @param cleanup
     *            whether or not to clean old versions
     * @param context the security context identifying the initiating user.
     */
    public static void update(RemoteService svc, Collection<Key> keys, boolean cleanup, SecurityContext context) {
        CommonRootResource rootRsrc = ResourceProvider.getResource(svc, CommonRootResource.class, context);
        CommonUpdateResource updRsrc = ResourceProvider.getResource(svc, CommonUpdateResource.class, context);

        Version apiVersion;
        Version serverVersion;
        try {
            apiVersion = updRsrc.getUpdateApiVersion();
            serverVersion = rootRsrc.getVersion();
        } catch (Exception e) {
            // API version (or server version) not (yet) supported, must be old BDeploy.
            if (log.isTraceEnabled()) {
                log.trace("API or server version not (yet) supported", e);
            }
            apiVersion = UPDATE_API_V1;
            serverVersion = SERVER_OLD_VERSION; // just use *any* older version, really. Not newer than 7.6.x
        }

        switch (apiVersion.getMajor()) {
            case 1:
                // We need to sort the updates so that the running OS is the last one
                streamFilteredKeys(keys, serverVersion).forEach(k -> updRsrc.updateV1(k.getKey(), cleanup));
                break;
            default:
                throw new UnsupportedOperationException("Update API version not supported: " + apiVersion);
        }
    }

    static Stream<ScopedManifestKey> streamFilteredKeys(Collection<Manifest.Key> keys, Version serverVersion) {
        return keys.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull).sorted((a, b) -> {
            // put own OS last.
            if (a.getOperatingSystem() != b.getOperatingSystem()) {
                return a.getOperatingSystem() == OsHelper.getRunningOs() ? 1 : -1;
            }
            return a.getKey().toString().compareTo(b.getKey().toString());
        }).filter(k -> {
            return k.getOperatingSystem() != OperatingSystem.LINUX_AARCH64 || serverVersion.compareTo(SERVER_LIMIT_VERSION) >= 0;
        });
    }

    public static Version currentApiVersion() {
        return UPDATE_API_V1;
    }

}

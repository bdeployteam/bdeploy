package io.bdeploy.interfaces;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.ZipHelper;

/**
 * Provides shared functionality for dealing with updates.
 */
public class UpdateHelper {

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
    private static final String SW_BDEPLOY = "bdeploy";

    /**
     * Directory (within the update dir) where to put the "to-be-installed" software.
     */
    public static final String UPDATE_DIR = "next";

    /**
     * This must match what the launcher script(s) expect to perform an update.
     */
    public static final int CODE_UPDATE = 42;

    private static final Logger log = LoggerFactory.getLogger(UpdateHelper.class);

    private UpdateHelper() {
    }

    /**
     * @param updateDir the update directory root
     * @return the path to the update directory to use.
     */
    public static Path prepareUpdateDirectory(Path updateDir) {
        Path updateTarget = updateDir.resolve(UpdateHelper.UPDATE_DIR);
        if (Files.isDirectory(updateTarget)) {
            log.warn("Removing stale update folder at {}", updateTarget);
            PathHelper.deleteRecursive(updateTarget);
        }
        return updateTarget;
    }

    /**
     * @param updateZipFile the update ZIP file containing the actual software
     * @param tmpDir a (unique) directory where to place temporary files and directories (e.g. when unpacking the ZIP file). The
     *            directory is assumed to be deleted by the caller.
     * @param hive the hive to import into. may be (but does not have to be) located inside the temp directory.
     * @return the {@link Key} of the imported {@link Manifest}.
     * @throws IOException
     */
    public static Manifest.Key importUpdate(Path updateZipFile, Path tmpDir, BHive hive) throws IOException {
        Manifest.Key key = calculateKeyFromDistZip(updateZipFile);

        // extract ZIP to src dir
        PathHelper.mkdirs(tmpDir);
        ZipHelper.unzip(updateZipFile, tmpDir);

        // expecting a single directory in the ZIP containing all the things.
        Path updContent = null;
        try (DirectoryStream<Path> content = Files.newDirectoryStream(tmpDir)) {
            for (Path path : content) {
                if (Files.isDirectory(path)) {
                    if (updContent != null) {
                        throw new IllegalStateException("More than one directory in update package");
                    }
                    updContent = path;
                }
            }
        }
        if (updContent == null) {
            throw new IllegalStateException("Cannot find update directory");
        }
        RuntimeAssert.assertTrue(Files.isDirectory(updContent), "Cannot find update content directory: " + updContent);
        Manifest.Key scopedKey = new Manifest.Key(SW_META_PREFIX + key.getName(), key.getTag());

        hive.execute(new ImportOperation().setSourcePath(updContent).setManifest(scopedKey));
        return scopedKey;
    }

    /**
     * Extract name and version from dist ZIP, as defined in gradle build.
     */
    private static Key calculateKeyFromDistZip(Path zip) {
        // check target OS for ZIP dist by checking filename.
        String name = SW_BDEPLOY;

        // find a file version.properties in the ZIP
        String project = null;
        String version = null;
        OperatingSystem os = null;
        boolean snapshot = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith("version.properties") && !entry.isDirectory()) {
                    Properties props = new Properties();
                    props.load(zipInputStream);

                    project = props.getProperty("project");
                    version = props.getProperty("version");
                    snapshot = Boolean.valueOf(props.getProperty("snapshot"));
                    os = OperatingSystem.valueOf(props.getProperty("os").toUpperCase());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot unzip update package", e);
        }

        if (SW_LAUNCHER.equals(project)) {
            name = project;
        }

        if (version == null) {
            throw new IllegalStateException("Cannot determin version for update package " + zip);
        }

        if (snapshot) {
            name += SW_SNAPSHOT;
        }

        return new ScopedManifestKey(name, os, version).getKey();
    }

}

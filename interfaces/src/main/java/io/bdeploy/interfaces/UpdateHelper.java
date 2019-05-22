package io.bdeploy.interfaces;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;

/**
 * Provides shared functionality for dealing with updates.
 */
public class UpdateHelper {

    /**
     * Prefix for all update package {@link Key}s.
     */
    private static final String SW_META_PREFIX = "meta/";

    /**
     * Suffix for 'snapshot' update packages (non-release-builds).
     */
    private static final String SW_SNAPSHOT = "/snapshot";

    /**
     * Name of the 'launcher' software.
     */
    private static final String SW_LAUNCHER = "launcher";

    /**
     * Name of the 'bdeploy' software.
     */
    private static final String SW_BDEPLOY = "bdeploy";

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
        unzip(updateZipFile, tmpDir);

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

    /**
     * Unzip single ZIP file
     */
    private static void unzip(final Path zipFile, final Path target) {
        // need to use commons compress to be able to unzip file attributes
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path path = target.resolve(entry.getName());
                if (!path.startsWith(target)) {
                    // path was absolute?!
                    throw new IllegalStateException("The given zip contains absolute paths: " + zipFile);
                }
                if (entry.isDirectory()) {
                    PathHelper.mkdirs(path);
                } else {
                    try (InputStream is = zf.getInputStream(entry)) {
                        Files.copy(is, path);
                    }
                    updatePermissions(path, entry.getUnixMode());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot unzip update package", e);
        }
    }

    private static void updatePermissions(Path file, int unixMode) throws IOException {
        if (isPosixFileStore(file)) {
            Set<PosixFilePermission> permissions = getPosixPermissionsAsSet(unixMode);
            if (!permissions.isEmpty()) {
                Files.setPosixFilePermissions(file, permissions);
            }
        }
    }

    private static boolean isPosixFileStore(Path path) throws IOException {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    private static Set<PosixFilePermission> getPosixPermissionsAsSet(int mode) {
        Set<PosixFilePermission> permissionSet = new HashSet<>();
        if ((mode & 0400) == 0400) {
            permissionSet.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) == 0200) {
            permissionSet.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) == 0100) {
            permissionSet.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) == 0040) {
            permissionSet.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) == 0020) {
            permissionSet.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) == 0010) {
            permissionSet.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) == 0004) {
            permissionSet.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) == 0002) {
            permissionSet.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) == 0001) {
            permissionSet.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return permissionSet;
    }

}

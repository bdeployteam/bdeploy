package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class ZipHelper {

    /**
     * Unzip single ZIP file
     */
    public static void unzip(final Path zipFile, final Path target) {
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

package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class ZipHelper {

    private static final int DEFAULT_NONEXEC_MODE = 0644;
    private static final int DEFAULT_EXEC_MODE = 0755;

    private ZipHelper() {
    }

    /** Determines wheter a given URI points to a ZIP file. */
    public static boolean isZipUri(URI uri) {
        return uri.getScheme().equals("jar") || (uri.getScheme().equals("file") && uri.toString().toLowerCase().endsWith(".zip"));
    }

    /**
     * Creates a ZIP file from the given source directory. The targets parent directory must exist.
     * <p>
     * In contrast to Java's built-in ZIP {@link FileSystem}, this will determine whether files should be executable and mark them
     * accordingly, even when run on windows.
     *
     * @param zipFile the ZIP file to create
     * @param source the source directory
     */
    public static void zip(Path zipFile, Path source) {
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(zipFile.toFile())) {
            internalZip(source, zaos);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + zipFile, e);
        }
    }

    /**
     * Creates a ZIP stream from the given source directory.
     * <p>
     * In contrast to Java's built-in ZIP {@link FileSystem}, this will determine whether files should be executable and mark them
     * accordingly, even when run on windows.
     *
     * @param output the stream to write to.
     * @param source the source directory
     */
    public static void zip(OutputStream output, Path source) {
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(output)) {
            internalZip(source, zaos);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot ZIP to stream", e);
        }
    }

    private static void internalZip(Path source, ZipArchiveOutputStream zaos) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // leave out the source directory itself, it would resolve to '/' - we don't want that!
                if (!dir.equals(source)) {
                    String entryName = PathHelper.separatorsToUnix(source.relativize(dir));
                    zaos.putArchiveEntry(zaos.createArchiveEntry(dir.toFile(), entryName));
                    zaos.closeArchiveEntry();
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                ZipArchiveEntry zae = new ZipArchiveEntry(file.toFile(), source.relativize(file).toString());

                if (PathHelper.isExecutable(PathHelper.getContentInfo(file, null))) {
                    zae.setUnixMode(DEFAULT_EXEC_MODE);
                } else {
                    zae.setUnixMode(DEFAULT_NONEXEC_MODE);
                }

                zaos.putArchiveEntry(zae);
                Files.copy(file, zaos);
                zaos.closeArchiveEntry();

                return super.visitFile(file, attrs);
            }
        });

        zaos.finish();
    }

    /**
     * Unzip single ZIP file
     */
    public static void unzip(Path zipFile, Path target) {
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
            throw new IllegalStateException("Cannot unzip " + zipFile, e);
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

    private static boolean isPosixFileStore(Path path) {
        return PathHelper.getPosixView(path) != null;
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

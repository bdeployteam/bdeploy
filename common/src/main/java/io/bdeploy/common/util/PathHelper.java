package io.bdeploy.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

import io.bdeploy.common.RetryableScope;

/**
 * Helps in handling different {@link String}s in the context of {@link Path}s.
 */
public class PathHelper {

    private static final int FILEOP_DELAY_MILLIS = 200;
    private static final int FILEOP_RETRIES = 50;
    private static final ContentInfoUtil CIU = loadCIU();

    private PathHelper() {
    }

    private static ContentInfoUtil loadCIU() {
        try (InputStreamReader rdr = new InputStreamReader(PathHelper.class.getClassLoader().getResourceAsStream("bdeploy-magic"),
                StandardCharsets.UTF_8)) {
            return new ContentInfoUtil(rdr);
        } catch (IOException e) {
            throw new IllegalStateException("ERROR: Cannot load magic resource", e);
        }
    }

    /**
     * Returns whether or not the given directory is empty. A non-existing directory is assumed to be empty.
     */
    public static boolean isDirEmpty(Path path) {
        if (!exists(path)) {
            return true;
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
            return !dirStream.iterator().hasNext();
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Converts the given string into a path object.
     *
     * @param path
     *            the path to convert.
     * @return the path or {@code null} if the input was null or empty
     */
    public static Path ofNullableStrig(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return Paths.get(path);
    }

    /**
     * Tests if the given location can be modified.
     */
    public static boolean isReadOnly(Path path) {
        try {
            PathHelper.mkdirs(path);
            Path testFile = path.resolve(UuidHelper.randomId());
            Files.newOutputStream(testFile).close();
            deleteIfExistsRetry(testFile);
            return false;
        } catch (Exception ioe) {
            return true;
        }
    }

    /**
     * Tests if the given location can be modified. Additionally the permissions are checked for consistency. Following conditions
     * lead to an exception:
     * <ul>
     * <li>Directory is read-only but we can write some files in there</li>
     * <li>Directory is writable but we cannot modify existing files</li>
     * <li>Directory is writable but we cannot delete files</li>
     * </ul>
     *
     * @param directory directory to check permissions. A new file will be created in there to check for write permissions
     * @param existingFile file that is already existing. File is tested if it can be opened for writing. The file must exist if
     *            not the check is skipped.
     * @return {@code true} if the root directory is read-only and {@code false} if files can be created / modified
     */
    public static boolean isReadOnly(Path directory, Path existingFile) {
        boolean canCreate = true;
        boolean canDelete = true;

        // Check if we can create a new file
        Path testFile = directory.resolve(UuidHelper.randomId());
        try {
            PathHelper.mkdirs(directory);
            Files.newOutputStream(testFile).close();
        } catch (Exception ioe) {
            canCreate = false;
        }

        // Check if we can delete the file
        if (canCreate) {
            try {
                PathHelper.deleteIfExistsRetry(testFile);
            } catch (Exception ioe) {
                canDelete = false;
            }
        }

        // Throw if we can create but not delete files
        if (canCreate && !canDelete) {
            throw new IllegalStateException("Inconsistent file and folder permissions: Missing permission to delete files.");
        }
        boolean readOnlyDir = !canCreate;

        // Check for consistent permissions if possible
        if (exists(existingFile)) {
            boolean writable = isWritable(existingFile);
            if (readOnlyDir && writable) {
                throw new IllegalStateException("Inconsistent file and folder permissions: Missing permission to create files.");
            }
            if (!readOnlyDir && !writable) {
                throw new IllegalStateException("Inconsistent file and folder permissions. Missing permission to modify files.");
            }
        }
        return readOnlyDir;
    }

    /**
     * Tests if the given file can be modified.
     * <p>
     * Implementation note: {@link Files#isWritable} reports wrong results and cannot be used as replacement. When advanced
     * permissions are granted where a user can can <tt>'Create Files/Write Data'</tt> but cannot
     * <tt>'Create Folders/Append Data'</tt> then this JAVA API returns 'true' where in reality trying to open the file for
     * writing is not denied. Trying to open new stream for writing does work more reliable and reports the correct result.
     * </p>
     */
    public static boolean isWritable(Path path) {
        try {
            Files.newOutputStream(path, StandardOpenOption.WRITE).close();
            return true;
        } catch (Exception ioe) {
            return false;
        }
    }

    /**
     * Create directories, wrapping {@link IOException} to {@link IllegalStateException}
     *
     * @param p {@link Path} to create.
     */
    public static void mkdirs(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + p, e);
        }
    }

    /**
     * Renames the given file or directory and then attempts to delete it.
     */
    public static void moveAndDelete(Path source, Path target) {
        moveRetry(source, target, StandardCopyOption.ATOMIC_MOVE);
        deleteRecursiveRetry(target);
    }

    /**
     * Converts all Windows separators ('\\') to the Unix separator ('/').
     */
    public static String separatorsToUnix(Path path) {
        return path.toString().replace("\\", "/");
    }

    /**
     * Returns the extension of a file.
     */
    public static String getExtension(String file) {
        int position = file.lastIndexOf('.');
        if (position == -1) {
            return "";
        }
        return file.substring(position + 1);
    }

    /**
     * @param zipFile the ZIP file to open (or create)
     * @return a {@link FileSystem} which can be used to access (and modify) the ZIP file.
     * @throws IOException
     */
    public static FileSystem openZip(Path zipFile) throws IOException {
        Map<String, Object> env = new TreeMap<>();
        env.put("create", "true");
        env.put("useTempFile", Boolean.TRUE);
        return FileSystems.newFileSystem(URI.create("jar:" + zipFile.toUri()), env);
    }

    /**
     * @param hint the {@link ContentInfo} to check whether it describes something which should be executable.
     * @return whether the file described by the given hint should be executable.
     */
    public static boolean isExecutable(ContentInfo hint) {
        if (hint == null) {
            return false;
        }

        if (hint.getMimeType() != null) {
            // match known mime types.
            switch (hint.getMimeType()) {
                case "application/x-sharedlib":
                case "application/x-executable":
                case "application/x-dosexec":
                case "application/x-mach-binary":
                case "text/x-shellscript":
                case "text/x-msdos-batch":
                    return true;
                default:
                    break;
            }
        }

        if (hint.getName() != null) {
            switch (hint.getName()) {
                case "ELF":
                case "32+":
                case "Mach-O":
                    return true;
                default:
                    break;
            }
        }

        if (hint.getMessage() != null && hint.getMessage().toLowerCase().contains("script text executable")) {
            // and additionally all with message containing:
            //  'script text executable' -> matches all shebangs (#!...) for scripts
            //  (this is due to https://github.com/j256/simplemagic/issues/59).
            return true;
        }

        return false;
    }

    /**
     * Determine the content type of the given file. A potentially pre-calculated {@link ContentInfo} will be passed through as-is
     * if given.
     *
     * @param child the path to check
     * @param hint the potential pre-calculated hint
     * @return a {@link ContentInfo} describing the file.
     * @throws IOException
     */
    public static ContentInfo getContentInfo(Path child, ContentInfo hint) throws IOException {
        // hint might have been calculated already while streaming file.
        if (hint == null) {
            try (InputStream is = Files.newInputStream(child)) {
                hint = CIU.findMatch(is);
            }
            if (hint == null) {
                // just any unknown file type.
                return null;
            }
        }
        return hint;
    }

    public static ContentInfoUtil getContentInfoUtil() {
        return CIU;
    }

    /**
     * Wrapper around Files.getFileAttributeView as it behaves differently than documented on JDK 17.
     *
     * @param path the path to get the view for.
     * @return a {@link PosixFileAttributeView} or <code>null</code> if not available.
     * @see "https://github.com/adoptium/adoptium-support/issues/363"
     */
    public static PosixFileAttributeView getPosixView(Path path) {
        try {
            return Files.getFileAttributeView(path, PosixFileAttributeView.class);
        } catch (Exception e) {
            // JDK 17 throws *undeclared* UnsupportedOperationException.
            return null;
        }
    }

    /**
     * Files.exists is incredible inefficient. Thus this helper does whatever is performance-wise best to determine if a path
     * exists.
     *
     * @param path the path to check
     * @return whether the path exists
     */
    public static boolean exists(Path path) {
        // we only optimize to toFile() in case of the default file-system.
        if (path.getFileSystem() != FileSystems.getDefault()) {
            return Files.exists(path);
        }

        return path.toFile().exists();
    }

    /**
     * @param path the {@link Path} to delete recursively.
     */
    public static void deleteRecursiveRetry(Path path) {
        if (!exists(path)) {
            return;
        }

        RetryableScope.create().withDelay(FILEOP_DELAY_MILLIS).withMaxRetries(FILEOP_RETRIES).run(() -> {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
            }
        });
    }

    /**
     * @param path the {@link Path} to delete.
     */
    public static void deleteIfExistsRetry(Path path) {
        RetryableScope.create().withDelay(FILEOP_DELAY_MILLIS).withMaxRetries(FILEOP_RETRIES)
                .run(() -> Files.deleteIfExists(path));
    }

    /**
     * @param source the {@link Path} which denotes the source file or directory.
     * @param target the {@link Path} which denotes the target file or directory.
     * @param options options as as accepted by {@link Files#move(Path, Path, CopyOption...)}
     */
    public static void moveRetry(Path source, Path target, CopyOption... options) {
        RetryableScope.create().withDelay(FILEOP_DELAY_MILLIS).withMaxRetries(FILEOP_RETRIES)
                .run(() -> Files.move(source, target, options));
    }
}

package io.bdeploy.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.UserPrincipal;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

/**
 * Helps in handling different {@link String}s in the context of {@link Path}s.
 */
public class PathHelper {

    private static final ContentInfoUtil CIU = loadCIU();

    private static ContentInfoUtil loadCIU() {
        try (InputStreamReader rdr = new InputStreamReader(PathHelper.class.getClassLoader().getResourceAsStream("bdeploy-magic"),
                StandardCharsets.UTF_8)) {
            return new ContentInfoUtil(rdr);
        } catch (IOException e) {
            throw new IllegalStateException("ERROR: Cannot load magic resource", e);
        }
    }

    private PathHelper() {
    }

    /**
     * Returns whether or not the given directory is empty. A non-existing directory is assumed to be empty.
     */
    public static boolean isDirEmpty(Path path) {
        if (!path.toFile().exists()) {
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
     * Tests if the given location can be modified. Testing is done by trying to create a new file.
     */
    public static boolean isReadOnly(Path path) {
        try {
            PathHelper.mkdirs(path);
            Path testFile = path.resolve(UuidHelper.randomId());
            Files.newOutputStream(testFile).close();
            Files.delete(testFile);
            return false;
        } catch (Exception ioe) {
            return true;
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
    public static boolean moveAndDelete(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            return false;
        }
        deleteRecursive(target);
        return true;
    }

    /**
     * @param path the {@link Path} to delete recursively.
     */
    public static void deleteRecursive(Path path) {
        if (!path.toFile().exists()) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete " + path, e);
        }
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

    /**
     * Sets the owner of the given path to the given user.
     */
    public static void setOwner(Path path, String owner) throws IOException {
        UserPrincipal current = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner);
        if (!Files.getOwner(path).getName().equals(current.getName())) {
            Files.setOwner(path, current);
        }
    }

    public static ContentInfoUtil getContentInfoUtil() {
        return CIU;
    }

}

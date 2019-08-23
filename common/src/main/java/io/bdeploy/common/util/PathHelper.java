package io.bdeploy.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private PathHelper() {
    }

    /**
     * Replace all problematic characters in a String with '_'.
     * <p>
     * ATTENTION: {@link String}s which are different may become the same (thus
     * {@link Path}s can collide) after processing.
     */
    public static String getPathFriendly(String str) {
        return str.replaceAll("\\W+", "_");
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
     * @param p the {@link Path} to measure size of
     * @return the size of the underlying file in bytes.
     */
    public static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read size of " + p, e);
        }
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
        int position = file.lastIndexOf(".");
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
                case "text/x-shellscript":
                case "text/x-msdos-batch":
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
            ContentInfoUtil util = new ContentInfoUtil();
            try (InputStream is = Files.newInputStream(child)) {
                hint = util.findMatch(is);
            }
            if (hint == null) {
                // just any unknown file type.
                return null;
            }
        }
        return hint;
    }

}

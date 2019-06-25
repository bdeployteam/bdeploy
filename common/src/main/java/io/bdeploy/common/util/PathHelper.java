package io.bdeploy.common.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

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

    public static FileSystem openZip(Path zipFile) throws IOException {
        Map<String, Object> env = new TreeMap<>();
        env.put("create", "true");
        env.put("useTempFile", Boolean.TRUE);
        return FileSystems.newFileSystem(URI.create("jar:" + zipFile.toUri()), env);
    }

}

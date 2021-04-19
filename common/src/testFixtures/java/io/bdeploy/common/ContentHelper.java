package io.bdeploy.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.bdeploy.common.util.PathHelper;

public class ContentHelper {

    public static final String ROOT_TREE_OID = "21a696bc0acb8c767b1b454338069288cc1e9bfa";
    public static final String DIR_TREE_OID = "34c086b59b3f96c954b7a7af9d3cb519f64a733a";
    public static final String SUBDIR_TREE_OID = "a550fa3c67161554cc44c978f9ae90ea570d73f4";

    public static final String TEST_TXT_OID = "b45cc3292e85e136c8b63b580c8774861d766304";
    public static final String DIR_TXT_OID = "31a4b2a8e38ee22ec80a94fd3ca88f68f6f460c0";
    public static final String SUBDIR_TXT_OID = "18fd659ea319d37272c37ca122f8af7de85263e0";

    public static final String TEST1 = "test file content 1";
    public static final String TEST2 = "test file content 2";
    public static final String TEST3 = "$(&*!/@äö?§x 🤣 \\n $(&*!\\";

    private static final int MAX_PATHS = 100;
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Random RND = new Random(1); // reproducible random numbers :)

    /**
     * Generate simple test source tree.
     */
    public static Path genSimpleTestTree(Path root, String name) throws IOException {
        Path tmp = root.resolve(name);

        Path t1 = tmp.resolve("test.txt");
        Path t2 = tmp.resolve(Paths.get("dir", "file.txt"));
        Path t3 = tmp.resolve(Paths.get("dir", "subDir", "child.txt"));

        PathHelper.mkdirs(t2.getParent());
        PathHelper.mkdirs(t3.getParent());
        Files.write(t1, TEST1.getBytes(StandardCharsets.UTF_8));
        Files.write(t2, TEST2.getBytes(StandardCharsets.UTF_8));
        Files.write(t3, TEST3.getBytes(StandardCharsets.UTF_8));

        return tmp;
    }

    public static void genTestTree(Path root, int num1K, int num4K, int num4M, int num16M, int num64M, int num128M)
            throws IOException {
        // totals to +/- 1.4GB
        genFiles(root, num1K, 1024);
        genFiles(root, num4K, 4 * 1024);
        genFiles(root, num4M, 4 * 1024 * 1024);
        genFiles(root, num16M, 16 * 1024 * 1024);
        genFiles(root, num64M, 64 * 1024 * 1024);
        genFiles(root, num128M, 128 * 1024 * 1024);
    }

    private static void genFiles(Path root, int num, int bytes) throws IOException {
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < num; ++i) {
            genTestFile(genTestDir(root, paths), bytes);
        }
    }

    private static Path genTestDir(Path root, List<Path> paths) {
        if (paths.size() >= MAX_PATHS) {
            int num = RND.nextInt(paths.size());
            return paths.get(num);
        }
        Path p = root.resolve(randomString(Math.max(5, RND.nextInt(50))));
        paths.add(p);
        return p;
    }

    public static Path genTestFile(Path dir, int byteCount) throws IOException {
        Path file = dir.resolve(randomString(Math.max(5, RND.nextInt(50))) + ".tmp");
        PathHelper.mkdirs(file.getParent());
        Files.write(file, randomString(byteCount).getBytes());
        return file;
    }

    public static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(RND.nextInt(AB.length())));
        }
        return sb.toString();
    }

    public static void checkDirsEqual(Path p1, Path p2) throws IOException {
        SortedMap<String, Long> p1_content = Files.walk(p1).filter(Files::isRegularFile).collect(Collectors
                .toMap(p -> p1.relativize(p).toString(), p -> p.toFile().length(), ContentHelper::mapMerge, TreeMap::new));

        SortedMap<String, Long> p2_content = Files.walk(p2).filter(Files::isRegularFile).collect(Collectors
                .toMap(p -> p2.relativize(p).toString(), p -> p.toFile().length(), ContentHelper::mapMerge, TreeMap::new));

        assertThat(p1_content, is(p2_content));
    }

    private static <U> U mapMerge(U u1, U u2) {
        throw new IllegalStateException("duplicate key " + u1);
    }

}

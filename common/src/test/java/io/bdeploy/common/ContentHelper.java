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

    public static final String ROOT_TREE_OID = "dc19eaeffb67b1ef808bc4cd9ce7a7c250a2bdd9";
    public static final String SUB_TREE_OID = "9fbb668dcbca30ca5a069c2f8a53a7d84c534e16";
    public static final String TEST_TXT_OID = "b45cc3292e85e136c8b63b580c8774861d766304";
    public static final String FILE_TXT_OID = "84ad17cded26722e0e87855df049aacc6401602e";

    public static final String T2_L1 = "test2 line 1 with some more contents";
    public static final String T2_L2 = "test2 line 2 with even more contents - but different $(&*!\\\\";

    public static final String TEST1 = "test file content 1";
    public static final String TEST2 = T2_L1 + "\n" + T2_L2;

    private static final int MAX_PATHS = 100;
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Random RND = new Random(1); // reproducible random numbers :)

    /**
     * Generate simple test source tree. Attention: test may hardcode object ids, so
     * DON'T change the content.
     */
    public static Path genSimpleTestTree(Path root, String name) throws IOException {
        Path tmp = root.resolve(name);

        // create some test files.
        Path t1 = tmp.resolve("test.txt");
        Path t2 = tmp.resolve(Paths.get("dir", "file.txt"));

        PathHelper.mkdirs(t2.getParent());
        Files.write(t1, TEST1.getBytes(StandardCharsets.UTF_8));
        Files.write(t2, TEST2.getBytes(StandardCharsets.UTF_8));

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
                .toMap(p -> p1.relativize(p).toString(), p -> PathHelper.sizeOf(p), ContentHelper::mapMerge, TreeMap::new));

        SortedMap<String, Long> p2_content = Files.walk(p2).filter(Files::isRegularFile).collect(Collectors
                .toMap(p -> p2.relativize(p).toString(), p -> PathHelper.sizeOf(p), ContentHelper::mapMerge, TreeMap::new));

        assertThat(p1_content, is(p2_content));
    }

    private static <U> U mapMerge(U u1, U u2) {
        throw new IllegalStateException("duplicate key " + u1);
    }

}

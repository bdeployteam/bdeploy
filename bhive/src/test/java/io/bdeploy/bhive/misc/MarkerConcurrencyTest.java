package io.bdeploy.bhive.misc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;

@ExtendWith(TestHive.class)
class MarkerConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(MarkerConcurrencyTest.class);

    @Test
    void testSequentialMarks(BHive hive, @TempDir Path tmp) throws Exception {

        // a test for manual comparison of performance when removing locking, e.g. as added with ce525e07 in MarkerDatabase

        Path source = tmp.resolve("source");
        Files.createDirectories(source);

        for (int i = 0; i < 1000; ++i) {
            Path x = source.resolve("file" + i);
            Files.write(x, Collections.singletonList("This is the content " + i));
        }

        long start = System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            hive.setParallelism(1);
            try (Transaction t = hive.getTransactions().begin()) {
                hive.execute(new ImportOperation().setSourcePath(source).setManifest(new Manifest.Key("test", "1.0")));
            }
        });

        log.info("Importing took {}ms", (System.currentTimeMillis() - start));
    }

    @Test
    void testConcurrentMarks(BHive hive, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("source");
        Files.createDirectories(source);

        for (int i = 0; i < 10; ++i) {
            Path x = source.resolve("file" + i);
            Files.write(x, Collections.singletonList("This is the content"));
        }

        assertDoesNotThrow(() -> {
            hive.setParallelism(10);
            try (Transaction t = hive.getTransactions().begin()) {
                hive.execute(new ImportOperation().setSourcePath(source).setManifest(new Manifest.Key("test", "1.0")));
            }
        });
    }

}

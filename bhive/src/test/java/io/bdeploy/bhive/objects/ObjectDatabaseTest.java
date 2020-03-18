/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class ObjectDatabaseTest extends DbTestBase {

    @Test
    public void testInsertSmallObject(@TempDir Path tmp, ActivityReporter r) throws IOException {
        String content = "This is a test";
        testWithContent(content, tmp, r);
    }

    @Test
    public void testInsertLargeObject(@TempDir Path tmp, ActivityReporter r) throws IOException {
        // generate a string with more than MAX_BUFFER_SIZE bytes (can be quite large).
        long minSize = ObjectDatabase.MAX_BUFFER_SIZE;

        StringBuilder builder = new StringBuilder();
        while (builder.length() <= minSize) {
            builder.append("aaabbbcccdddeeefffggghhhiiijjjkkklllmmmnnnooopppqqqrrrssstttuuuvvvwwwxxxyyyzzz");
        }

        testWithContent(builder.toString(), tmp, r);
    }

    private void testWithContent(String content, Path tmp, ActivityReporter r) throws IOException {
        Path fileToAdd = tmp.resolve("obj.tmp");
        ObjectId id;
        Files.write(fileToAdd, Collections.singleton(content));

        id = getObjectDatabase().addObject(fileToAdd);
        assertTrue(getObjectDatabase().hasObject(id));

        ObjectDatabase other = new ObjectDatabase(getObjectDatabasePath(), getObjectDatabasePath(), r);
        assertTrue(other.hasObject(id));

        Path expFile = tmp.resolve("obj2.tmp");
        try (InputStream stream = getObjectDatabase().getStream(id)) {
            Files.copy(stream, expFile);
        }
        List<String> reread = Files.readAllLines(expFile);
        assertThat(reread.get(0), is(content));
    }

    @SlowTest
    @ParameterizedTest
    @ValueSource(ints = { 512, 1024, 4096, 1024 * 1024 })
    public void compareStreamWithInMemoryHash(int fsz, @TempDir Path tmp, ActivityReporter r) throws IOException {
        Path tmpDir = tmp.resolve("stream");
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            paths.add(ContentHelper.genTestFile(tmpDir, fsz));
        }
        Activity stream = r.start("Inserting " + fsz + " bytes via Stream...");
        for (Path p : paths) {
            try (InputStream is = Files.newInputStream(p)) {
                getObjectDatabase().addObject(is);
            }
        }
        stream.done();

        Path tmpDir2 = tmp.resolve("inmem");
        paths.clear();
        for (int i = 0; i < 100; ++i) {
            paths.add(ContentHelper.genTestFile(tmpDir2, fsz));
        }
        Activity inMem = r.start("Inserting " + fsz + " bytes in-memory...");
        for (Path p : paths) {
            byte[] bytes = Files.readAllBytes(p);
            getObjectDatabase().addObject(bytes);
        }
        inMem.done();
    }

}

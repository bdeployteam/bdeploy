/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TempDirectory.TempDir;

/**
 * Runs all tests from {@link ObjectDatabaseTest} but with a ZIP compressed DB.
 */
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class ZipObjectDatabaseTest extends ObjectDatabaseTest {

    private ObjectDatabase zipDb;
    private Path zipPath;
    private Path dbRoot;
    private FileSystem zfs;

    @BeforeEach
    public void initZipDb(@TempDir Path tmp, ActivityReporter r) throws IOException {
        zipPath = tmp.resolve("zip-hive.zip");
        Map<String, Object> env = new TreeMap<>();
        env.put("create", "true");
        env.put("useTempFile", Boolean.TRUE);
        zfs = FileSystems.newFileSystem(URI.create("jar:" + zipPath.toUri()), env);

        dbRoot = zfs.getPath("/");
        zipDb = new ObjectDatabase(dbRoot, tmp.resolve("objtmp"), r);
    }

    @AfterEach
    public void cleanZipDb() throws IOException {
        zfs.close();
        zfs = null;
    }

    @Override
    public ObjectDatabase getObjectDatabase() {
        return zipDb;
    }

    @Override
    public Path getObjectDatabasePath() {
        return dbRoot;
    }

}

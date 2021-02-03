/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.util.PathHelper;

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
        zfs = PathHelper.openZip(zipPath);

        dbRoot = zfs.getPath("/");
        zipDb = new ObjectDatabase(dbRoot, tmp.resolve("objtmp"), r, null);
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

/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TestActivityReporter.class)
@ExtendWith(TempDirectory.class)
public class DbTestBase {

    private Path dbPath;
    private ObjectDatabase db;

    @BeforeEach
    private void initDb(@TempDir Path tmp, ActivityReporter reporter) throws IOException {
        dbPath = tmp.resolve("objdb");
        db = new ObjectDatabase(dbPath, dbPath, reporter);
    }

    protected ObjectDatabase getObjectDatabase() {
        return db;
    }

    protected Path getObjectDatabasePath() {
        return dbPath;
    }

    public static ObjectId randomId() {
        byte[] bytes = UUID.randomUUID().toString().getBytes();
        return ObjectId.create(bytes, 0, bytes.length);
    }

}

/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TestActivityReporter.class)
public class DbTestBase {

    private Path dbPath;
    private ObjectDatabase db;

    @BeforeEach
    void initDb(@TempDir Path tmp, ActivityReporter reporter) throws IOException {
        dbPath = tmp.resolve("objdb");
        db = new ObjectDatabase(dbPath, tmp.resolve("objtmp"), reporter, null);
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

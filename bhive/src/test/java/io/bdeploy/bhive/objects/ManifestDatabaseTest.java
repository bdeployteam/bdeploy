/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;

class ManifestDatabaseTest extends DbTestBase {

    @Test
    void testStoreAndRead(@TempDir Path tmp) {
        Path dbDir = tmp.resolve("manifests");
        ObjectId ojectId = ObjectId.parse("000fb6b1d68b158a996dc17fa3e137a5f2751757");

        Manifest.Key key = new Manifest.Key("test/app1", "v1.0");
        try (ManifestDatabase db = new ManifestDatabase(dbDir)) {
            Manifest.Builder mf = new Manifest.Builder(key).setRoot(ojectId).addLabel("test", "label");
            db.addManifest(mf.build(null), false);
        }

        try (ManifestDatabase db = new ManifestDatabase(dbDir)) {
            assertTrue(db.hasManifest(key));

            Manifest m = db.getManifest(key);
            assertThat(m, is(notNullValue()));
            assertThat(m.getLabels().get("test"), is("label"));

            assertFalse(db.getAllManifests().isEmpty());
            assertIterableEquals(db.getAllManifests(), db.getAllForName("test/app1"));
            assertIterableEquals(db.getAllForName("test"), db.getAllForName("test/app1"));

            db.removeManifest(key);

        }

        try (ManifestDatabase db = new ManifestDatabase(dbDir)) {
            assertFalse(db.hasManifest(key));
        }
    }
}

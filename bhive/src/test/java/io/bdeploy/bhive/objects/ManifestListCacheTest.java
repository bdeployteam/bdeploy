package io.bdeploy.bhive.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;

class ManifestListCacheTest extends DbTestBase {

    @Test
    void testStoreAndRead(@TempDir Path tmp) {
        Path dbDir = tmp.resolve("manifests");

        Manifest.Key key1 = new Manifest.Key("test/app1", "v1.0");
        Manifest.Key key2 = new Manifest.Key("xtest/app2/x", "v1.0");
        Manifest.Key key3 = new Manifest.Key("xtest/app1/x", "v1.0");
        Manifest.Key key4 = new Manifest.Key("xtest/app2/x", "v2.0");

        ObjectId ojectId1 = ObjectId.parse("001c4d13605b848cda4429cfa8646e7379e07024");
        ObjectId ojectId2 = ObjectId.parse("001e5ba6f6d0da7d241cbd241b5255f31227bb91");
        ObjectId ojectId3 = ObjectId.parse("1a1d239fb0ae469b2ccf198d1f960f918d462389");
        ObjectId ojectId4 = ObjectId.parse("1a2fb269120648bc126c1108e5f9ee3db7aa3b3a");

        try (ManifestDatabase db = new ManifestDatabase(dbDir)) {
            db.addManifest(new Manifest.Builder(key1).setRoot(ojectId1).build(null), false);
            db.addManifest(new Manifest.Builder(key2).setRoot(ojectId2).build(null), false);
        }

        try (ManifestDatabase db = new ManifestDatabase(dbDir.resolve("../manifests"))) {
            assertTrue(db.hasManifest(key1));
            assertTrue(db.hasManifest(key2));
            assertFalse(db.hasManifest(key3));
            assertFalse(db.hasManifest(key4));

            assertEquals(2, db.getAllManifests().size());
            assertEquals(1, db.getAllForName("test").size());
            assertEquals(1, db.getAllForName("xtest").size());
            assertEquals(1, db.getAllForName("xtest/app2").size());

            db.addManifest(new Manifest.Builder(key3).setRoot(ojectId3).build(null), false);
            db.addManifest(new Manifest.Builder(key4).setRoot(ojectId4).build(null), false);

            assertTrue(db.hasManifest(key3));
            assertTrue(db.hasManifest(key4));

            assertEquals(4, db.getAllManifests().size());
            assertEquals(1, db.getAllForName("test").size());
            assertEquals(3, db.getAllForName("xtest").size());
            assertEquals(2, db.getAllForName("xtest/app2").size());

            db.removeManifest(key3);
            db.removeManifest(key4);

            assertFalse(db.hasManifest(key3));
            assertFalse(db.hasManifest(key4));

            assertEquals(2, db.getAllManifests().size());
            assertEquals(1, db.getAllForName("test").size());
            assertEquals(1, db.getAllForName("xtest").size());
            assertEquals(1, db.getAllForName("xtest/app2").size());
        }
    }
}

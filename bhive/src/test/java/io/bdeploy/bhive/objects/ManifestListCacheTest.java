package io.bdeploy.bhive.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest;

public class ManifestListCacheTest extends DbTestBase {

    @Test
    void storeAndRead(@TempDir Path tmp) throws IOException {
        Path dbDir = tmp.resolve("manifests");

        Manifest.Key key1 = new Manifest.Key("test/app1", "v1.0");
        Manifest.Key key2 = new Manifest.Key("xtest/app2/x", "v1.0");
        Manifest.Key key3 = new Manifest.Key("xtest/app1/x", "v1.0");
        Manifest.Key key4 = new Manifest.Key("xtest/app2/x", "v2.0");

        try (ManifestDatabase db = new ManifestDatabase(dbDir)) {
            db.addManifest(new Manifest.Builder(key1).setRoot(randomId()).build(null), false);
            db.addManifest(new Manifest.Builder(key2).setRoot(randomId()).build(null), false);
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

            db.addManifest(new Manifest.Builder(key3).setRoot(randomId()).build(null), false);
            db.addManifest(new Manifest.Builder(key4).setRoot(randomId()).build(null), false);

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

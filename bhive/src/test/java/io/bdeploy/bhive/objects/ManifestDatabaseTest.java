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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
public class ManifestDatabaseTest extends DbTestBase {

    @Test
    public void storeAndRead(@TempDir Path tmp) throws IOException {
        Path dbDir = tmp.resolve("manifests");

        ManifestDatabase db = new ManifestDatabase(dbDir);

        Manifest.Key key = new Manifest.Key("test/app1", "v1.0");
        Manifest.Builder mf = new Manifest.Builder(key).setRoot(randomId()).addLabel("test", "label");

        db.addManifest(mf.build(null));

        db = new ManifestDatabase(dbDir);
        assertTrue(db.hasManifest(key));

        Manifest m = db.getManifest(key);
        assertThat(m, is(notNullValue()));
        assertThat(m.getLabels().get("test"), is("label"));

        assertFalse(db.getAllManifests().isEmpty());
        assertIterableEquals(db.getAllManifests(), db.getAllForName("test/app1"));
        assertIterableEquals(db.getAllForName("test"), db.getAllForName("test/app1"));

        db.removeManifest(key);

        db = new ManifestDatabase(dbDir);
        assertFalse(db.hasManifest(key));
    }

}

package io.bdeploy.bhive.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class MetaManifestTest {

    private static final class MyMeta {

        public String value;
        public long timestamp;
    }

    @Test
    void crudMeta(BHive hive, @TempDir Path temp) throws IOException {
        MyMeta metaShared = new MyMeta();
        metaShared.value = "test";
        metaShared.timestamp = System.currentTimeMillis();

        MyMeta metaMf1 = new MyMeta();
        metaMf1.value = "forMf1";
        metaMf1.timestamp = System.currentTimeMillis();

        MyMeta metaMf2 = new MyMeta();
        metaMf2.value = "forMf2";
        metaMf2.timestamp = System.currentTimeMillis();

        Manifest.Key testMf1 = new Manifest.Key("my/test", "v1");
        Manifest.Key testMf2 = new Manifest.Key("my/test", "v2");

        Path tree = ContentHelper.genSimpleTestTree(temp, "test");

        hive.execute(new ImportOperation().setSourcePath(tree).setManifest(testMf1));
        hive.execute(new ImportOperation().setSourcePath(tree).setManifest(testMf2));

        MetaManifest<MyMeta> sharedMM = new MetaManifest<>(testMf2, false, MyMeta.class);
        assertNull(sharedMM.read(hive));
        Key initialWrite = sharedMM.write(hive, metaShared);
        assertNotNull(initialWrite);
        assertNotNull(sharedMM.read(hive));
        assertEquals(metaShared.timestamp, sharedMM.read(hive).timestamp);
        assertNotNull(sharedMM.remove(hive));
        assertNull(sharedMM.read(hive));
        assertNotNull(sharedMM.write(hive, metaShared));

        metaShared.value = "other value";
        assertNotNull(sharedMM.write(hive, metaShared));

        // initial version of manifest should be too old and deleted.
        assertFalse(hive.execute(new ManifestExistsOperation().setManifest(initialWrite)));

        MetaManifest<MyMeta> mf1MM = new MetaManifest<>(testMf1, true, MyMeta.class);
        assertNull(mf1MM.read(hive));
        assertNotNull(mf1MM.write(hive, metaMf1));
        assertEquals(metaMf1.value, mf1MM.read(hive).value);

        MetaManifest<MyMeta> mf2MM = new MetaManifest<>(testMf2, true, MyMeta.class);
        assertNull(mf2MM.read(hive));
        assertNotNull(mf2MM.write(hive, metaMf2));
        assertEquals(metaMf2.value, mf2MM.read(hive).value);
        assertNotNull(mf2MM.remove(hive));

        assertNotNull(mf1MM.read(hive));
        assertEquals(metaMf1.value, mf1MM.read(hive).value);
        assertNotNull(sharedMM.read(hive));
        assertEquals(metaShared.value, sharedMM.read(hive).value);
    }

}

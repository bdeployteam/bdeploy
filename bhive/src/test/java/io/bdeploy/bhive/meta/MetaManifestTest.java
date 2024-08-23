package io.bdeploy.bhive.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.common.ContentHelper;

@ExtendWith(TestHive.class)
class MetaManifestTest {

    private static final class MyMeta {

        public String value;
        public long timestamp;
    }

    @Test
    void testCrudMeta(BHive hive, @TempDir Path temp) throws IOException {
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

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setSourcePath(tree).setManifest(testMf1));
            hive.execute(new ImportOperation().setSourcePath(tree).setManifest(testMf2));
        }

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
        Key mmSkey = sharedMM.write(hive, metaShared);
        assertNotNull(mmSkey);

        // initial version of manifest should be too old and deleted.
        assertFalse(hive.execute(new ManifestExistsOperation().setManifest(initialWrite)));

        MetaManifest<MyMeta> mf1MM = new MetaManifest<>(testMf1, true, MyMeta.class);
        assertNull(mf1MM.read(hive));
        Key mm1key = mf1MM.write(hive, metaMf1);
        assertNotNull(mm1key);
        assertEquals(metaMf1.value, mf1MM.read(hive).value);

        MetaManifest<MyMeta> mf2MM = new MetaManifest<>(testMf2, true, MyMeta.class);
        assertNull(mf2MM.read(hive));
        Key mm2key = mf2MM.write(hive, metaMf2);
        assertNotNull(mm2key);
        assertEquals(metaMf2.value, mf2MM.read(hive).value);
        assertNotNull(mf2MM.remove(hive));

        assertNotNull(mf1MM.read(hive));
        assertEquals(metaMf1.value, mf1MM.read(hive).value);
        assertNotNull(sharedMM.read(hive));
        assertEquals(metaShared.value, sharedMM.read(hive).value);

        TreeSet<Key> empty = new TreeSet<>();
        hive.execute(new ManifestDeleteOperation().setToDelete(testMf2));
        assertFalse(MetaManifest.isParentAlive(mm2key, hive, empty));
        assertTrue(MetaManifest.isParentAlive(mm1key, hive, empty));
        assertTrue(MetaManifest.isParentAlive(mmSkey, hive, empty));

        hive.execute(new ManifestDeleteOperation().setToDelete(testMf1));
        assertFalse(MetaManifest.isParentAlive(mm2key, hive, empty));
        assertFalse(MetaManifest.isParentAlive(mm1key, hive, empty));
        assertFalse(MetaManifest.isParentAlive(mmSkey, hive, empty));

    }

}

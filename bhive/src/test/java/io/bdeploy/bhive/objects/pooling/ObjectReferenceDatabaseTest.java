package io.bdeploy.bhive.objects.pooling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectReferenceDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TestActivityReporter.class)
class ObjectReferenceDatabaseTest {

    @Test
    void testReferences(@TempDir Path tmp, ActivityReporter reporter) {
        ObjectReferenceDatabase ord = new ObjectReferenceDatabase(tmp, reporter);

        byte[] a = { 0xC, 0x0, 0xF, 0xF, 0xE };
        byte[] b = { 0xB, 0xA, 0xB, 0xE };

        ObjectId idA = ObjectId.create(a, 0, a.length);
        ObjectId idB = ObjectId.create(b, 0, b.length);

        ord.addReference(idA, "BHiveA");

        assertEquals(1, ord.read(idA).size());
        assertEquals(0, ord.read(idB).size());

        ord.addReference(idA, "BHiveB");

        assertEquals(2, ord.read(idA).size());
        assertEquals(0, ord.read(idB).size());
        assertEquals("BHiveA", ord.read(idA).first());
        assertEquals("BHiveB", ord.read(idA).last());

        ord.addReference(idB, "BHiveA");

        assertEquals(2, ord.read(idA).size());
        assertEquals(1, ord.read(idB).size());
        assertEquals("BHiveA", ord.read(idA).first());
        assertEquals("BHiveB", ord.read(idA).last());
        assertEquals("BHiveA", ord.read(idB).first());
    }

}

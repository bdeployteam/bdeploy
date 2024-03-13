package io.bdeploy.bhive.objects.pooling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.CollectingConsumer;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.ReadOnlyObjectDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.util.StreamHelper;

@ExtendWith(TestActivityReporter.class)
class ReadOnlyObjectDatabaseTest {

    @Test
    void testReadOnly(@TempDir Path tmp, ActivityReporter reporter) throws Exception {
        ObjectDatabase odb = new ObjectDatabase(tmp, tmp.resolve("tmp"), reporter, null);

        byte[] a = new byte[] { 0xD, 0xE, 0xA, 0xD };
        byte[] b = new byte[] { 0xC, 0x0, 0xF, 0xE };
        byte[] c = new byte[] { 0xB, 0xA, 0xB, 0xE };

        ObjectId idA = odb.addObject(a);
        ObjectId idB = odb.addObject(b);

        ReadOnlyObjectDatabase rodb = new ReadOnlyObjectDatabase(tmp, reporter);

        assertArrayEquals(a, StreamHelper.read(rodb.getStream(idA)));
        assertArrayEquals(b, StreamHelper.read(rodb.getStream(idB)));

        List<ObjectId> allObjects = CollectingConsumer.collect(rodb::walkAllObjects);
        assertEquals(2, allObjects.size());
        assertTrue(allObjects.contains(idA));
        assertTrue(allObjects.contains(idB));

        assertThrows(UnsupportedOperationException.class, () -> rodb.removeObject(idA));
        assertThrows(UnsupportedOperationException.class, () -> rodb.addObject(c));

        allObjects = CollectingConsumer.collect(rodb::walkAllObjects);
        assertEquals(2, allObjects.size());
        assertTrue(allObjects.contains(idA));
        assertTrue(allObjects.contains(idB));
    }
}

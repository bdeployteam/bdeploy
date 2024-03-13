package io.bdeploy.bhive.objects.pooling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.CollectingConsumer;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.AugmentedObjectDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.ReadOnlyObjectDatabase;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.util.StreamHelper;

@ExtendWith(TestActivityReporter.class)
class AugmentedObjectDatabaseTests {

    @Test
    void testAugment(@TempDir Path tmp, ActivityReporter reporter) throws Exception {
        ObjectDatabase odb = new ObjectDatabase(tmp.resolve("augment"), tmp.resolve("augment/tmp"), reporter, null);

        byte[] a = new byte[] { 0xD, 0xE, 0xA, 0xD };
        byte[] b = new byte[] { 0xC, 0x0, 0xF, 0xE };
        byte[] c = new byte[] { 0xB, 0xA, 0xB, 0xE };

        ObjectId idA = odb.addObject(a);
        ObjectId idB = odb.addObject(b);

        AugmentedObjectDatabase aodb = new AugmentedObjectDatabase(tmp.resolve("local"), tmp.resolve("local/tmp"), reporter, null,
                new ReadOnlyObjectDatabase(tmp.resolve("augment"), reporter));

        assertTrue(aodb.hasObject(idA));
        assertTrue(aodb.hasObject(idB));

        ObjectId newIdA = aodb.addObject(a);
        assertEquals(idA, newIdA);
        assertTrue(CollectingConsumer.collect(odb::walkAllObjects).contains(newIdA));
        assertFalse(CollectingConsumer.collect(aodb::walkAllObjects).contains(newIdA));

        ObjectId idC = aodb.addObject(c);
        assertTrue(CollectingConsumer.collect(aodb::walkAllObjects).contains(idC));
        assertFalse(CollectingConsumer.collect(odb::walkAllObjects).contains(idC));

        assertArrayEquals(a, StreamHelper.read(aodb.getStream(idA)));
        assertArrayEquals(b, StreamHelper.read(aodb.getStream(idB)));
        assertArrayEquals(c, StreamHelper.read(aodb.getStream(idC)));
    }
}

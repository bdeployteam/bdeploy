package io.bdeploy.minion.deploy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
class DataFileTest {

    @Test
    void testDataFile(BHive local, MasterRootResource master, CommonRootResource common, RemoteService remote, @TempDir Path tmp,
            MinionRoot mr) throws IOException {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                .get(InstanceManifest.INSTANCE_LABEL);

        master.getNamedMaster("demo").install(instance);
        master.getNamedMaster("demo").activate(instance, false);

        long beforeWrite = System.currentTimeMillis() - 10_000; // filesystem dependent resolution.

        // fake write a file to the data directory.
        Path dataDir = mr.getDeploymentDir().resolve(id).resolve("data");
        PathHelper.mkdirs(dataDir); // just in case.
        Path testFile = dataDir.resolve("test.txt");

        Files.write(testFile, "Test".getBytes(StandardCharsets.UTF_8));

        List<RemoteDirectory> dds = master.getNamedMaster("demo").getDataDirectorySnapshots(id);

        assertEquals(1, dds.size()); // only master node
        RemoteDirectory idd = dds.get(0);
        assertEquals("master", idd.minion);
        assertEquals(id, idd.id);
        assertEquals(1, idd.entries.size());

        assertEquals("test.txt", idd.entries.get(0).path);
        assertEquals(4, idd.entries.get(0).size);
        assertTrue(idd.entries.get(0).lastModified >= beforeWrite);

        Path testFile2 = dataDir.resolve("sub/test.txt");
        PathHelper.mkdirs(testFile2.getParent());

        Files.write(testFile2, "Test".getBytes(StandardCharsets.UTF_8));

        dds = master.getNamedMaster("demo").getDataDirectorySnapshots(id);

        assertEquals(1, dds.size()); // only master node
        idd = dds.get(0);
        assertEquals("master", idd.minion);
        assertEquals(id, idd.id);
        assertEquals(2, idd.entries.size());

        List<RemoteDirectoryEntry> sorted = new ArrayList<>(idd.entries);
        sorted.sort((a, b) -> a.path.compareTo(b.path));

        assertEquals("sub/test.txt", sorted.get(0).path);
        assertEquals(4, sorted.get(0).size);
        assertTrue(sorted.get(0).lastModified >= beforeWrite);

        assertEquals("test.txt", sorted.get(1).path);
        assertEquals(4, sorted.get(1).size);
        assertTrue(sorted.get(1).lastModified >= beforeWrite);

        RemoteDirectoryEntry sub = sorted.get(0);

        EntryChunk chunk = master.getNamedMaster("demo").getEntryContent("master", sub, 0, 0);
        assertArrayEquals("Test".getBytes(StandardCharsets.UTF_8), chunk.content);

        // test partial fetch.
        Files.write(testFile2, Collections.singletonList("Line 1"));

        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, 0, 0);
        assertArrayEquals(("Line 1" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), chunk.content);

        long offset = chunk.endPointer;
        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, offset, 0);
        assertNull(chunk); // no changes - actually 204 no content

        Files.write(testFile2, Collections.singletonList("Line 2"), StandardOpenOption.APPEND);
        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, offset, 0);
        assertArrayEquals(("Line 2" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), chunk.content);
        offset = chunk.endPointer;

        Files.write(testFile2, Collections.singletonList("Line 3"), StandardOpenOption.APPEND);
        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, offset, 0);
        assertArrayEquals(("Line 3" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), chunk.content);

        // test re-reading with offset and limit
        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, 0, 6);
        assertArrayEquals("Line 1".getBytes(StandardCharsets.UTF_8), chunk.content);

        chunk = master.getNamedMaster("demo").getEntryContent("master", sub, 6 + System.lineSeparator().length(), 6);
        assertArrayEquals("Line 2".getBytes(StandardCharsets.UTF_8), chunk.content);
    }

}

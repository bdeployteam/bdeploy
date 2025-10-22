package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.dto.StringEntryChunkDto;
import jakarta.ws.rs.BadRequestException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
class DataFilesTest {

    @Test
    void testDataFiles(BHive local, CommonRootResource master, InstanceGroupResource igr, RemoteService remote, @TempDir Path tmp)
            throws Exception {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, true);

        InstanceManifest im = InstanceManifest.of(local, instance);
        InstanceResource ir = igr.getInstanceResource("demo");

        ir.install(im.getConfiguration().id, instance.getTag());
        ir.activate(im.getConfiguration().id, instance.getTag(), false);

        FileStatusDto newFile = new FileStatusDto();
        newFile.type = FileStatusType.ADD;
        newFile.file = "test.txt";
        newFile.content = Base64.getEncoder().encodeToString("Test String".getBytes(StandardCharsets.UTF_8));

        ir.updateDataFiles(im.getConfiguration().id, "master", Collections.singletonList(newFile));

        assertThrows(BadRequestException.class, () -> {
            ir.updateDataFiles(im.getConfiguration().id, "master", Collections.singletonList(newFile));
        });

        assertThrows(BadRequestException.class, () -> {
            FileStatusDto badFile = new FileStatusDto();
            badFile.type = FileStatusType.ADD;
            badFile.file = "../../test.txt";
            badFile.content = newFile.content;
            ir.updateDataFiles(im.getConfiguration().id, "master", Collections.singletonList(badFile));
        });

        List<RemoteDirectory> snapshot = ir.getProcessResource(im.getConfiguration().id).getDataDirSnapshot();
        assertEquals(1, snapshot.size());

        RemoteDirectory dataDir = snapshot.get(0);
        assertEquals(im.getConfiguration().id, dataDir.id);
        assertEquals("master", dataDir.minion);
        assertEquals(1, dataDir.entries.size());

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            String token = ir.getContentStreamRequest(dataDir.id, dataDir.minion, dataDir.entries.get(0));
            StreamHelper.copy(ir.getContentStream(dataDir.id, token).readEntity(InputStream.class), os);
            assertEquals("Test String", os.toString(StandardCharsets.UTF_8)); // response stream NOT encoded!
        }

        StringEntryChunkDto contentChunk = ir.getContentChunk(dataDir.id, dataDir.minion, dataDir.entries.get(0), 0, 4096);
        assertNotNull(contentChunk.content);
        assertEquals("Test String", contentChunk.content);

        RemoteDirectory fdd = dataDir;
        assertThrows(BadRequestException.class, () -> {
            var entry = fdd.entries.get(0);
            entry.path = "../../" + entry.path;
            ir.getContentChunk(fdd.id, fdd.minion, entry, 0, 4096);
        });

        FileStatusDto updateFile = new FileStatusDto();
        updateFile.type = FileStatusType.EDIT;
        updateFile.file = "test.txt";
        updateFile.content = Base64.getEncoder().encodeToString("Another Test".getBytes(StandardCharsets.UTF_8));

        ir.updateDataFiles(im.getConfiguration().id, "master", Collections.singletonList(updateFile));

        snapshot = ir.getProcessResource(im.getConfiguration().id).getDataDirSnapshot();
        assertEquals(1, snapshot.size());

        dataDir = snapshot.get(0);
        assertEquals(im.getConfiguration().id, dataDir.id);
        assertEquals("master", dataDir.minion);
        assertEquals(1, dataDir.entries.size());

        contentChunk = ir.getContentChunk(dataDir.id, dataDir.minion, dataDir.entries.get(0), 0, 4096);
        assertEquals("Another Test", contentChunk.content);

        FileStatusDto deleteFile = new FileStatusDto();
        deleteFile.type = FileStatusType.DELETE;
        deleteFile.file = "test.txt";

        ir.updateDataFiles(im.getConfiguration().id, "master", Collections.singletonList(deleteFile));

        snapshot = ir.getProcessResource(im.getConfiguration().id).getDataDirSnapshot();
        assertEquals(1, snapshot.size());
        assertEquals(0, snapshot.get(0).entries.size());
    }

}

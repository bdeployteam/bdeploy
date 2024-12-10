package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.DownloadService;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import io.bdeploy.ui.api.SoftwareResource;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(TestMinion.class)
@ExtendWith(TestActivityReporter.class)
class RepoResourceTest {

    @Test
    void testCrud(SoftwareRepositoryResource repos) {
        assertTrue(repos.list().isEmpty());

        SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryConfiguration();
        cfg.name = "demo";
        cfg.description = "description";

        repos.create(cfg);

        List<SoftwareRepositoryConfiguration> list = repos.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).name);
        assertEquals("description", list.get(0).description);

        cfg.description = "other";
        repos.update("demo", cfg);

        list = repos.list();
        assertEquals(1, list.size());
        assertEquals("demo", list.get(0).name);
        assertEquals("other", list.get(0).description);

        // name mismatch
        assertThrows(WebApplicationException.class, () -> repos.update("test", cfg));

        SoftwareRepositoryConfiguration read = repos.read("demo");
        assertEquals("demo", read.name);
        assertEquals("other", read.description);

        repos.delete("demo");

        assertTrue(repos.list().isEmpty());
    }

    @Test
    void testSw(SoftwareRepositoryResource repos, DownloadService dlService, @TempDir Path tmp, ActivityReporter reporter)
            throws IOException {
        assertTrue(repos.list().isEmpty());

        SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryConfiguration();
        cfg.name = "demo";
        cfg.description = "description";

        repos.create(cfg);

        SoftwareResource swr = repos.getSoftwareResource("demo");
        assertTrue(swr.list(true, true).isEmpty());

        // create test app
        Path sw = tmp.resolve("sw");
        PathHelper.mkdirs(sw);
        Files.write(sw.resolve("software.txt"), Collections.singleton("This is the software content"));

        Manifest.Key swKey = new Manifest.Key("sw", "1.0.0");

        // create zipped hive
        Path zip = tmp.resolve("tmp.zip");
        try (BHive hive = new BHive(zip.toUri(), null, reporter); Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setSourcePath(sw).setManifest(swKey));
        }

        swr.upload(FormDataHelper.createMultiPartForStream("file", new FileInputStream(zip.toFile())));

        assertTrue(swr.list(true, true).contains(swKey));

        String token = swr.createSoftwareZipFile(swKey.getName(), swKey.getTag());
        Response download = dlService.download(token);
        assertEquals(200, download.getStatus());

        Path zip2 = tmp.resolve("tmp2.zip");
        try (InputStream is = download.readEntity(InputStream.class)) {
            Files.copy(is, zip2);
        }

        Path sw2 = tmp.resolve("sw2");
        try (BHive hive = new BHive(zip2.toUri(), null, reporter)) {
            hive.execute(new ExportOperation().setManifest(swKey).setTarget(sw2));
        }

        ContentHelper.checkDirsEqual(sw, sw2);

        swr.delete(swKey.getName(), swKey.getTag());
        assertTrue(swr.list(true, true).isEmpty());
    }

}

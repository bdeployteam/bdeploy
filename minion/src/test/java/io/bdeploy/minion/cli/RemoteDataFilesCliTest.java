package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteDataFilesTool;
import io.bdeploy.ui.cli.RemoteDeploymentTool;
import jakarta.ws.rs.BadRequestException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteDataFilesCliTest extends BaseMinionCliTest {

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp) throws IOException {
        /* create file for upload and directory for export */
        Path tempFile = Files.createTempFile(null, null);
        Path tempDirectory = Files.createTempDirectory(null);
        Files.write(tempFile, "testing data files\n".getBytes(StandardCharsets.UTF_8));
        try {
            Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

            String id = local.execute(new ManifestLoadOperation().setManifest(instance)).getLabels()
                    .get(InstanceManifest.INSTANCE_LABEL);

            remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                    "--install");

            remote(remote, RemoteDeploymentTool.class, "--instanceGroup=demo", "--uuid=" + id, "--version=" + instance.getTag(),
                    "--activate");

            StructuredOutput result;
            Exception ex;
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");

            /* must specify either --list, --export, --upload or --delete */
            ex = assertThrows(IllegalArgumentException.class, () -> {
                remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id);
            });
            assertEquals(
                    "Please specify what you want to do by enabling one of the flags: --list, --export, --upload or --delete",
                    ex.getMessage());

            /* must specify only one flag. either --list, --export, --upload or --delete */
            ex = assertThrows(IllegalArgumentException.class, () -> {
                remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list", "--upload");
            });
            assertEquals("You can enable only one flag at a time: --list, --export, --upload or --delete", ex.getMessage());

            /* no data files are present yet */
            assertEquals(0, result.size());

            /* upload a data file as file1 */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--upload",
                    "--fileSource=" + tempFile.toString(), "--fileTarget=file1", "--targetNode=master");
            assertEquals("Success", result.get(0).get("message"));
            assertEquals("file1", result.get(0).get("UploadedFileAs"));

            /* uploading existing file1 without --force will fail */
            ex = assertThrows(BadRequestException.class, () -> {
                remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--upload",
                        "--fileSource=" + tempFile.toString(), "--fileTarget=file1", "--targetNode=master");
            });
            assertTrue(ex.getMessage().contains("Cannot update file1 in aaa-bbb-ccc"));

            /* uploading existing file1 with --force will succeed */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--upload",
                    "--fileSource=" + tempFile.toString(), "--fileTarget=file1", "--targetNode=master", "--force");
            assertEquals("Success", result.get(0).get("message"));
            assertEquals("file1", result.get(0).get("UploadedFileAs"));

            /* upload a data file as file2 */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--upload",
                    "--fileSource=" + tempFile.toString(), "--fileTarget=file2", "--targetNode=master");
            assertEquals("Success", result.get(0).get("message"));
            assertEquals("file2", result.get(0).get("UploadedFileAs"));

            /* list returns two uploaded data files: file1 and file2 */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
            assertEquals(2, result.size());

            /* don't assume order, might be locale dependent. */
            var x = new ArrayList<String>();
            x.add(result.get(0).get("Path"));
            x.add(result.get(1).get("Path"));
            assertTrue(x.contains("file1"));
            assertTrue(x.contains("file2"));

            /* list with --filter */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list",
                    "--filter=file2");
            assertEquals(1, result.size());
            assertEquals("file2", result.get(0).get("Path"));

            /* export */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--export",
                    "--exportTo=" + tempDirectory.toString());
            assertEquals("Success", result.get(0).get("message"));
            assertEquals(tempDirectory.toString(), result.get(0).get("ExportedFilesTo"));

            /* delete only file2 using --filter */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--delete",
                    "--filter=file2");
            assertEquals("Success", result.get(0).get("message"));
            assertEquals("Successfully deleted 1 files", result.get(0).get("DeleteFiles"));

            /* file1 remains */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
            assertEquals(1, result.size());
            assertEquals("file1", result.get(0).get("Path"));

            /* delete remaining file1 */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--delete");
            assertEquals("Success", result.get(0).get("message"));
            assertEquals("Successfully deleted 1 files", result.get(0).get("DeleteFiles"));

            /* no files remain */
            result = remote(remote, RemoteDataFilesTool.class, "--instanceGroup=demo", "--uuid=" + id, "--list");
            assertEquals(0, result.size());
        } finally {
            PathHelper.deleteRecursiveRetry(tempFile);
            PathHelper.deleteRecursiveRetry(tempDirectory);
        }
    }
}

package io.bdeploy.jersey.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;

@ExtendWith(TempDirectory.class)
public class StreamTest {

    private static Path src;

    StreamTestResourceImpl impl = new StreamTestResourceImpl(src);

    @RegisterExtension
    TestServer ext = new TestServer(impl);

    @BeforeAll
    static void prepare(@TempDir Path tmp) throws Exception {
        src = ContentHelper.genTestFile(tmp, 1024 * 1024);
    }

    @BeforeEach
    void setClientOutput(JerseyClientFactory factory) {
        factory.setReporter(new ActivityReporter.Stream(System.err));
    }

    @Test
    void download(StreamTestResource rs) throws Exception {
        byte[] sourceBytes = Files.readAllBytes(src);

        Path localFile = rs.download();

        Thread.sleep(200); // might take some time on windows..
        assertFalse(Files.exists(src)); // deleted after writing.

        byte[] targetBytes = Files.readAllBytes(localFile);

        assertEquals(sourceBytes.length, targetBytes.length);
        assertArrayEquals(sourceBytes, targetBytes);
    }

    @Test
    void upload(StreamTestResource rs) throws IOException {
        rs.upload(src);

        byte[] sourceBytes = Files.readAllBytes(src);
        byte[] targetBytes = Files.readAllBytes(impl.getTargetFile());

        assertEquals(sourceBytes.length, targetBytes.length);
        assertArrayEquals(sourceBytes, targetBytes);
    }

}

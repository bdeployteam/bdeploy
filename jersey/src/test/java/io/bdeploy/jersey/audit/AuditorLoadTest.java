package io.bdeploy.jersey.audit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
public class AuditorLoadTest {

    @Test
    void testAuditorLoad(@TempDir Path tmp) {
        try (RollingFileAuditor a = new RollingFileAuditor(tmp)) {
            // with the current default log config, ~10_000 entries of the size generated below fit into
            // a single audit.log/audit.json file. 30_000 is well enough to rotate at least once :)
            for (int i = 0; i < 30_000; ++i) {
                if (i % 1000 == 0) {
                    System.out.println("writing: " + i);
                }
                Map<String, String> params = new TreeMap<>();
                for (int y = 0; y < 10; ++y) {
                    params.put(ContentHelper.randomString(10), Long.toString(System.currentTimeMillis()));
                }
                a.audit(AuditRecord.Builder.fromSystem().addParameters(params).setMessage(ContentHelper.randomString(20))
                        .build());
            }
        }

        assertTrue(Files.isRegularFile(tmp.resolve("audit-1.log.gz")));
        assertTrue(Files.isRegularFile(tmp.resolve("audit-1.json.gz")));
    }

}

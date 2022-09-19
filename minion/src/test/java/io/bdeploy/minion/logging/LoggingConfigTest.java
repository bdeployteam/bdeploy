package io.bdeploy.minion.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestMinion.class)
@ExtendWith(TestActivityReporter.class)
class LoggingConfigTest {

    @Test
    void updateConfig(MinionStatusResource msr, @TempDir Path tmp, MinionRoot mr) throws Exception {
        String dummyContent = "<Configuration/>";
        Path dummyCfg = tmp.resolve("log4j2.xml");
        Files.write(dummyCfg, Collections.singletonList(dummyContent));

        msr.setLoggerConfig(dummyCfg);

        Path target = mr.getRootDir().resolve("etc/log4j2.xml");
        List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);

        assertEquals(1, lines.size());
        assertEquals(dummyContent, lines.get(0));
    }

    @Test
    void readFiles(CommonRootResource crr, MinionRoot mr) throws Exception {
        Files.write(mr.getLogDir().resolve("server.log"), Collections.singleton("log1"));
        Files.write(mr.getLogDir().resolve("server-1.log"), Collections.singleton("log2"));

        var dirs = crr.getLogDirectories(null);

        assertEquals(1, dirs.size());
        assertEquals("master", dirs.get(0).minion);
        assertNull(dirs.get(0).problem);
        assertNull(dirs.get(0).id);
        assertEquals(3, dirs.get(0).entries.size()); // the two logs, audit.log, audit.json

        var entries = dirs.get(0).entries;
        entries.sort((a, b) -> b.path.compareTo(a.path)); // reverse lexical

        assertTrue(entries.get(0).path.endsWith("server.log"));

        EntryChunk content = crr.getLogContent(dirs.get(0).minion, entries.get(0), 0, entries.get(0).size);
        assertEquals("log1", new String(content.content, StandardCharsets.UTF_8).trim()); // without newline
    }

}

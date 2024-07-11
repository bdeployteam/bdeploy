package io.bdeploy.launcher.cli.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.launcher.LauncherPathProvider;
import io.bdeploy.launcher.cli.scripts.impl.LocalFileAssocScriptHelper;
import io.bdeploy.launcher.cli.scripts.impl.LocalStartScriptHelper;

/**
 * Contains tests for scripts related classes.
 */
public class ScriptsTest {

    private static final Path homeDir = Path.of("test").toAbsolutePath();
    private final LauncherPathProvider lpp = new LauncherPathProvider(homeDir);

    @Test
    void testScriptUtils() {
        String applicationId = "appId";
        assertEquals("BDeploy." + applicationId + ".1", ScriptUtils.getBDeployFileAssocId(applicationId));

        assertEquals(".bar", ScriptUtils.getFullFileExtension(".bar"));
        assertEquals(".bar", ScriptUtils.getFullFileExtension("bar"));

        for (OperatingSystem os : OperatingSystem.values()) {
            assertNull(ScriptUtils.getStartScriptIdentifier(os, null));
            assertNull(ScriptUtils.getFileAssocIdentifier(os, null));

            if (os == OperatingSystem.WINDOWS) {
                assertEquals("test.bat", ScriptUtils.getStartScriptIdentifier(os, "test.bat"));
                assertEquals("test.bat", ScriptUtils.getStartScriptIdentifier(os, "test"));
                assertEquals("test.foo.bat", ScriptUtils.getStartScriptIdentifier(os, "test.foo.bat"));
                assertEquals("test.foo.bat", ScriptUtils.getStartScriptIdentifier(os, "test.foo"));

                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "start.test.bat"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "start.test"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "test.bat"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "test"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, ".test.bat"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, ".test"));
            } else {
                assertEquals("test", ScriptUtils.getStartScriptIdentifier(os, "test"));
                assertEquals("test.bat", ScriptUtils.getStartScriptIdentifier(os, "test.bat"));
                assertEquals("test.foo", ScriptUtils.getStartScriptIdentifier(os, "test.foo"));
                assertEquals("test.foo.bat", ScriptUtils.getStartScriptIdentifier(os, "test.foo.bat"));

                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "start.test.bat"));
                assertEquals("start.test", ScriptUtils.getFileAssocIdentifier(os, "start.test"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, "test.bat"));
                assertEquals("start.test", ScriptUtils.getFileAssocIdentifier(os, "test"));
                assertEquals("start.test.bat", ScriptUtils.getFileAssocIdentifier(os, ".test.bat"));
                assertEquals("start.test", ScriptUtils.getFileAssocIdentifier(os, ".test"));
            }
        }
    }

    @Test
    void testStartScriptHelper() {
        ClientApplicationConfiguration cfg = getClientAppConfigWithProcessControl();
        cfg.appDesc.processControl.startScriptName = "test";

        assertEquals("test.bat",//
                new LocalStartScriptHelper(OperatingSystem.WINDOWS, null, lpp).calculateScriptName(cfg));
        assertEquals("test",//
                new LocalStartScriptHelper(OperatingSystem.UNKNOWN, null, lpp).calculateScriptName(cfg));
    }

    @Test
    void testFileAssocScriptHelper() {
        ClientApplicationConfiguration cfg = getClientAppConfigWithProcessControl();
        cfg.appDesc.processControl.fileAssocExtension = "test";

        assertEquals("start.test.bat",//
                new LocalFileAssocScriptHelper(OperatingSystem.WINDOWS, null, lpp).calculateScriptName(cfg));
        assertEquals("start.test",//
                new LocalFileAssocScriptHelper(OperatingSystem.UNKNOWN, null, lpp).calculateScriptName(cfg));
    }

    private static ClientApplicationConfiguration getClientAppConfigWithProcessControl() {
        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.appDesc = new ApplicationDescriptor();
        cfg.appDesc.processControl = new ProcessControlDescriptor();
        return cfg;
    }
}

package io.bdeploy.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.launcher.LauncherPathProvider.SpecialDirectory;

/**
 * Contains unit tests for {@link LauncherPathProvider}.
 */
public class LauncherPathProviderTest {

    private static final Path homeDir = Path.of("test").toAbsolutePath();
    private static final Path appsDir = homeDir.resolve("apps");
    private static final Path bhiveDir = homeDir.resolve("bhive");
    private static final Path launcherDir = homeDir.resolve("launcher");
    private static final Path logsDir = homeDir.resolve("logs");
    private static final Path poolDir = appsDir.resolve("pool");
    private static final Path startScriptsDir = appsDir.resolve("start_scripts");
    private static final Path fileAssocScriptsDir = appsDir.resolve("file_assoc_scripts");
    private static final Path launcherBinDir = launcherDir.resolve("bin");
    private static final String applicationId = "dummy";
    private static final String defaultTag = "1";
    private static final DeploymentPathProvider dpp = new DeploymentPathProvider(appsDir, null, applicationId, defaultTag);

    private final LauncherPathProvider lpp = new LauncherPathProvider(homeDir);

    @Test
    void testWithoutApplicationId() {
        assertEquals(homeDir, lpp.get(SpecialDirectory.HOME));
        assertEquals(appsDir, lpp.get(SpecialDirectory.APPS));
        assertEquals(bhiveDir, lpp.get(SpecialDirectory.BHIVE));
        assertEquals(launcherDir, lpp.get(SpecialDirectory.LAUNCHER));
        assertEquals(logsDir, lpp.get(SpecialDirectory.LOGS));
        assertEquals(poolDir, lpp.get(SpecialDirectory.MANIFEST_POOL));
        assertEquals(startScriptsDir, lpp.get(SpecialDirectory.START_SCRIPTS));
        assertEquals(fileAssocScriptsDir, lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS));
        assertEquals(launcherBinDir, lpp.get(SpecialDirectory.LAUNCHER_BIN));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.APP));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.CONFIG));
    }

    @Test
    void testWithApplicationId() {
        lpp.setApplicationId(applicationId);
        Path appDir = appsDir.resolve(applicationId);
        Path appBinTagDir = appDir.resolve("bin").resolve(defaultTag);

        assertEquals(homeDir, lpp.get(SpecialDirectory.HOME));
        assertEquals(appsDir, lpp.get(SpecialDirectory.APPS));
        assertEquals(bhiveDir, lpp.get(SpecialDirectory.BHIVE));
        assertEquals(launcherDir, lpp.get(SpecialDirectory.LAUNCHER));
        assertEquals(logsDir, lpp.get(SpecialDirectory.LOGS));
        assertEquals(poolDir, lpp.get(SpecialDirectory.MANIFEST_POOL));
        assertEquals(startScriptsDir, lpp.get(SpecialDirectory.START_SCRIPTS));
        assertEquals(fileAssocScriptsDir, lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS));
        assertEquals(launcherBinDir, lpp.get(SpecialDirectory.LAUNCHER_BIN));
        assertEquals(appDir, lpp.get(SpecialDirectory.APP));
        assertEquals(appBinTagDir, lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertEquals(appBinTagDir.resolve("config"), lpp.get(SpecialDirectory.CONFIG));
    }

    @Test
    void testCompareToDeploymentPathProvider() {
        lpp.setApplicationId(applicationId);

        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT),
                lpp.get(SpecialDirectory.APP));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG),
                lpp.get(SpecialDirectory.CONFIG));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN),
                lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL),
                lpp.get(SpecialDirectory.MANIFEST_POOL));
    }

    @Test
    void testDeploymentPathProviderConversion() {
        assertThrows(RuntimeException.class, () -> lpp.toDeploymentPathProvider());
        lpp.setApplicationId(applicationId);
        DeploymentPathProvider convertedDpp = lpp.toDeploymentPathProvider();

        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.RUNTIME),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.RUNTIME));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.DATA),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.DATA));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.LOG_DATA),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.LOG_DATA));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL));
        assertEquals(dpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.INSTANCE_MANIFEST_POOL),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.INSTANCE_MANIFEST_POOL));
    }
}

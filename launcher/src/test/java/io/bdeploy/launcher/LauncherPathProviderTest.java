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
class LauncherPathProviderTest {

    private static final Path HOME_DIR = Path.of("test").toAbsolutePath();
    private static final Path APPS_DIR = HOME_DIR.resolve("apps");
    private static final Path BHIVE_DIR = HOME_DIR.resolve("bhive");
    private static final Path LAUNCHER_DIR = HOME_DIR.resolve("launcher");
    private static final Path LOGS_DIR = HOME_DIR.resolve("logs");
    private static final Path POOL_DIR = APPS_DIR.resolve("pool");
    private static final Path START_SCRIPTS_DIR = APPS_DIR.resolve("start_scripts");
    private static final Path FILE_ASSOC_SCRIPTS_DIR = APPS_DIR.resolve("file_assoc_scripts");
    private static final Path LAUNCHER_BIN_DIR = LAUNCHER_DIR.resolve("bin");
    private static final String APPLICATION_ID = "dummy";
    private static final String DEFAULT_TAG = "1";
    private static final DeploymentPathProvider DPP = new DeploymentPathProvider(APPS_DIR, null, APPLICATION_ID, DEFAULT_TAG);

    private final LauncherPathProvider lpp = new LauncherPathProvider(HOME_DIR);

    @Test
    void testWithoutApplicationId() {
        assertEquals(HOME_DIR, lpp.get(SpecialDirectory.HOME));
        assertEquals(APPS_DIR, lpp.get(SpecialDirectory.APPS));
        assertEquals(BHIVE_DIR, lpp.get(SpecialDirectory.BHIVE));
        assertEquals(LAUNCHER_DIR, lpp.get(SpecialDirectory.LAUNCHER));
        assertEquals(LOGS_DIR, lpp.get(SpecialDirectory.LOGS));
        assertEquals(POOL_DIR, lpp.get(SpecialDirectory.MANIFEST_POOL));
        assertEquals(START_SCRIPTS_DIR, lpp.get(SpecialDirectory.START_SCRIPTS));
        assertEquals(FILE_ASSOC_SCRIPTS_DIR, lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS));
        assertEquals(LAUNCHER_BIN_DIR, lpp.get(SpecialDirectory.LAUNCHER_BIN));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.APP));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertThrows(RuntimeException.class, () -> lpp.get(SpecialDirectory.CONFIG));
    }

    @Test
    void testWithApplicationId() {
        lpp.setApplicationId(APPLICATION_ID);
        Path appDir = APPS_DIR.resolve(APPLICATION_ID);
        Path appBinTagDir = appDir.resolve("bin").resolve(DEFAULT_TAG);

        assertEquals(HOME_DIR, lpp.get(SpecialDirectory.HOME));
        assertEquals(APPS_DIR, lpp.get(SpecialDirectory.APPS));
        assertEquals(BHIVE_DIR, lpp.get(SpecialDirectory.BHIVE));
        assertEquals(LAUNCHER_DIR, lpp.get(SpecialDirectory.LAUNCHER));
        assertEquals(LOGS_DIR, lpp.get(SpecialDirectory.LOGS));
        assertEquals(POOL_DIR, lpp.get(SpecialDirectory.MANIFEST_POOL));
        assertEquals(START_SCRIPTS_DIR, lpp.get(SpecialDirectory.START_SCRIPTS));
        assertEquals(FILE_ASSOC_SCRIPTS_DIR, lpp.get(SpecialDirectory.FILE_ASSOC_SCRIPTS));
        assertEquals(LAUNCHER_BIN_DIR, lpp.get(SpecialDirectory.LAUNCHER_BIN));
        assertEquals(appDir, lpp.get(SpecialDirectory.APP));
        assertEquals(appBinTagDir, lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertEquals(appBinTagDir.resolve("config"), lpp.get(SpecialDirectory.CONFIG));
    }

    @Test
    void testCompareToDeploymentPathProvider() {
        lpp.setApplicationId(APPLICATION_ID);

        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT),
                lpp.get(SpecialDirectory.APP));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG),
                lpp.get(SpecialDirectory.CONFIG));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN),
                lpp.get(SpecialDirectory.APP_BIN_TAG));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL),
                lpp.get(SpecialDirectory.MANIFEST_POOL));
    }

    @Test
    void testDeploymentPathProviderConversion() {
        assertThrows(RuntimeException.class, lpp::toDeploymentPathProvider);
        lpp.setApplicationId(APPLICATION_ID);
        DeploymentPathProvider convertedDpp = lpp.toDeploymentPathProvider();

        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.ROOT));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.CONFIG));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.RUNTIME),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.RUNTIME));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.BIN));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.DATA),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.DATA));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.LOG_DATA),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.LOG_DATA));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.MANIFEST_POOL));
        assertEquals(DPP.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.INSTANCE_MANIFEST_POOL),
                convertedDpp.get(io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory.INSTANCE_MANIFEST_POOL));
    }
}

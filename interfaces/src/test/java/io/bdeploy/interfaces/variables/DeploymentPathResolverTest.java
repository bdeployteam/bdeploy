package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DeploymentPathResolverTest {

    private static final String ITEM_ID = "id";
    private static final String ITEM_TAG = "1";

    private static final Path DEPLOYMENT_DIR = Path.of("deployment").toAbsolutePath();
    private static final Path APP_DIR = DEPLOYMENT_DIR.resolve(ITEM_ID);
    private static final Path DATA_DIR = APP_DIR.resolve("data");
    private static final Path BIN_DIR = APP_DIR.resolve("bin").resolve(ITEM_TAG);
    private static final Path CONFIG_DIR = BIN_DIR.resolve("config");
    private static final Path RUNTIME_DIR = BIN_DIR.resolve("runtime");
    private static final Path INSTANCE_MANIFEST_POOL_DIR = APP_DIR.resolve("pool");
    private static final Path MANIFEST_POOL_DIR = DEPLOYMENT_DIR.resolve("pool");
    private static final Path LOG_DATA_DIR = Path.of("log_data").toAbsolutePath();

    private static final String ROOT_DIR_STRING = APP_DIR.toString();
    private static final String DATA_DIR_STRING = DATA_DIR.toString();
    private static final String BIN_DIR_STRING = BIN_DIR.toString();
    private static final String CONFIG_DIR_STRING = CONFIG_DIR.toString();
    private static final String RUNTIME_DIR_STRING = RUNTIME_DIR.toString();
    private static final String INSTANCE_MANIFEST_POOL_DIR_STRING = INSTANCE_MANIFEST_POOL_DIR.toString();
    private static final String MANIFEST_POOL_DIR_STRING = MANIFEST_POOL_DIR.toString();

    @Test
    void testDeploymentPathResolverWithLogDataDir() {
        var dpp = new DeploymentPathProvider(DEPLOYMENT_DIR, LOG_DATA_DIR, ITEM_ID, ITEM_TAG);
        var resolver = new DeploymentPathResolver(dpp);

        // Check error cases
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("UNKNOWN_DIRECTORY"));

        // Check happy cases
        assertEquals(ROOT_DIR_STRING, resolver.doResolve("ROOT"));
        assertEquals(DATA_DIR_STRING, resolver.doResolve("DATA"));
        assertEquals(BIN_DIR_STRING, resolver.doResolve("BIN"));
        assertEquals(CONFIG_DIR_STRING, resolver.doResolve("CONFIG"));
        assertEquals(RUNTIME_DIR_STRING, resolver.doResolve("RUNTIME"));
        assertEquals(INSTANCE_MANIFEST_POOL_DIR_STRING, resolver.doResolve("INSTANCE_MANIFEST_POOL"));
        assertEquals(MANIFEST_POOL_DIR_STRING, resolver.doResolve("MANIFEST_POOL"));
        assertEquals(LOG_DATA_DIR.resolve(ITEM_ID).toString(), resolver.doResolve("LOG_DATA"));
    }

    @Test
    void testDeploymentPathResolverWithoutLogDataDir() {
        var dpp = new DeploymentPathProvider(DEPLOYMENT_DIR, null, ITEM_ID, ITEM_TAG);
        var resolver = new DeploymentPathResolver(dpp);

        // Check error cases
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("UNKNOWN_DIRECTORY"));

        // Check happy cases
        assertEquals(ROOT_DIR_STRING, resolver.doResolve("ROOT"));
        assertEquals(DATA_DIR_STRING, resolver.doResolve("DATA"));
        assertEquals(BIN_DIR_STRING, resolver.doResolve("BIN"));
        assertEquals(CONFIG_DIR_STRING, resolver.doResolve("CONFIG"));
        assertEquals(RUNTIME_DIR_STRING, resolver.doResolve("RUNTIME"));
        assertEquals(INSTANCE_MANIFEST_POOL_DIR_STRING, resolver.doResolve("INSTANCE_MANIFEST_POOL"));
        assertEquals(MANIFEST_POOL_DIR_STRING, resolver.doResolve("MANIFEST_POOL"));
        assertEquals(DATA_DIR_STRING, resolver.doResolve("LOG_DATA"));
    }
}

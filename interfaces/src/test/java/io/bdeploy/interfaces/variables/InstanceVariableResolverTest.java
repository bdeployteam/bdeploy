package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

class InstanceVariableResolverTest {

    @Test
    void testInstanceVariableResolver() {
        var config1 = new InstanceNodeConfiguration();
        config1.purpose = null;
        config1.id = "id1";
        config1.name = "Human readable name of config1";
        config1.product = null;

        var config2 = new InstanceNodeConfiguration();
        config2.purpose = InstancePurpose.TEST;
        config2.id = "id2";
        config2.name = "Human readable name of config2";
        config2.product = new Manifest.Key("test/product", "1");

        Path appDir = Path.of("appDir").toAbsolutePath();

        var resolver1 = new InstanceVariableResolver(config1, null, "1");
        var resolver2 = new InstanceVariableResolver(config2, appDir, "1");

        // Test invalid input
        assertNull(resolver1.doResolve("UNKNOWN_VARIABLE"));
        assertNull(resolver2.doResolve("UNKNOWN_VARIABLE"));

        // Test valid input
        assertEquals("", resolver1.doResolve("SYSTEM_PURPOSE"));
        assertEquals("id1", resolver1.doResolve("ID"));
        assertEquals("id1", resolver1.doResolve("UUID")); //TODO deprecated -> remove
        assertEquals("1", resolver1.doResolve("TAG"));
        assertEquals("Human readable name of config1", resolver1.doResolve("NAME"));
        assertEquals("", resolver1.doResolve("PRODUCT_ID"));
        assertEquals("", resolver1.doResolve("PRODUCT_TAG"));
        assertEquals(InstanceDeploymentInformationApi.FILE_NAME, resolver1.doResolve("DEPLOYMENT_INFO_FILE"));

        assertEquals(InstancePurpose.TEST.name(), resolver2.doResolve("SYSTEM_PURPOSE"));
        assertEquals("id2", resolver2.doResolve("ID"));
        assertEquals("id2", resolver2.doResolve("UUID")); //TODO deprecated -> remove
        assertEquals("1", resolver2.doResolve("TAG"));
        assertEquals("Human readable name of config2", resolver2.doResolve("NAME"));
        assertEquals("test/product", resolver2.doResolve("PRODUCT_ID"));
        assertEquals("1", resolver2.doResolve("PRODUCT_TAG"));
        assertEquals(appDir.resolve(InstanceDeploymentInformationApi.FILE_NAME).toString(),
                resolver2.doResolve("DEPLOYMENT_INFO_FILE"));
    }
}

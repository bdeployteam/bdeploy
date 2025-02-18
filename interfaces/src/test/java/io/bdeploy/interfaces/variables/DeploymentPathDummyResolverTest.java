package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DeploymentPathDummyResolverTest {

    @Test
    void testDeploymentPathValidationDummyResolver() {
        var resolver = new DeploymentPathDummyResolver();

        // Check error cases
        assertNull(resolver.doResolve(""));
        assertNull(resolver.doResolve("   "));
        assertNull(resolver.doResolve("abc"));
        assertNull(resolver.doResolve("abcdefghijklmnopqrstuvwxyz"));
        assertNull(resolver.doResolve("0123456789"));
        assertNull(resolver.doResolve("{[()]}²³"));

        // Check happy cases
        Arrays.stream(DeploymentPathProvider.SpecialDirectory.values())
                .forEach(dir -> assertNotNull(resolver.doResolve(dir.name())));
    }
}

package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DeploymentPathValidationDummyResolverTest {

    @Test
    void testDeploymentPathValidationDummyResolver() {
        var resolver = new DeploymentPathValidationDummyResolver();

        // Check error cases
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve(""));
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("   "));
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("abc"));
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("abcdefghijklmnopqrstuvwxyz"));
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("0123456789"));
        assertThrows(IllegalArgumentException.class, () -> resolver.doResolve("{[()]}²³"));

        // Check happy cases
        Arrays.stream(DeploymentPathProvider.SpecialDirectory.values())
                .forEach(dir -> assertNotNull(resolver.doResolve(dir.name())));
    }
}

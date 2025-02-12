package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManifestVariableDummyResolverTest {

    @Test
    void testManifestVariableValidationDummyResolver() {
        var resolver = new ManifestVariableDummyResolver();
        ResolverTestHelper.TEST_STRINGS.forEach(s -> assertEquals(s, resolver.doResolve(s)));
    }
}

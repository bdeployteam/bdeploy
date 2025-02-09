package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EnvironmentVariableDummyResolverTest {

    @Test
    void testEnvironmentVariableDummyResolver() {
        var resolver = new EnvironmentVariableDummyResolver();
        ResolverTestHelper.TEST_STRINGS.forEach(s -> assertNotNull(resolver.doResolve(s)));
    }
}

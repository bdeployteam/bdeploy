package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnvironmentVariableResolverTest {

    @Test
    void testEnvironmentVariableResolver() {
        var resolver = new EnvironmentVariableResolver();
        ResolverTestHelper.TEST_STRINGS.forEach(s -> assertEquals(System.getenv(s), resolver.apply(s)));
    }
}

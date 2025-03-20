package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EmptyVariableResolverTest {

    @Test
    void testEmptyVariableResolver() {
        var resolver = new EmptyVariableResolver();
        ResolverTestHelper.TEST_STRINGS.forEach(s -> assertNotNull(resolver.apply(s)));
    }
}

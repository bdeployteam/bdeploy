package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ApplicationVariableResolverTest {

    private static final String APP_ID = "APP_ID";
    private static final String APP_NAME = "APP_NAME";
    private static final ApplicationVariableResolver RESOLVER = new ApplicationVariableResolver(
            ResolverTestHelper.getAppConfig(APP_ID, APP_NAME));

    @Test
    void testInvalid() {
        assertNull(RESOLVER.doResolve("invalid"));
    }

    @Test
    void testApplicationVariableResolverID() {
        assertEquals(APP_ID, RESOLVER.doResolve("ID"));
    }

    @Test
    void testApplicationVariableResolverNAME() {
        assertEquals(APP_NAME, RESOLVER.doResolve("NAME"));
    }

    @Deprecated(since = "7.6.0")
    @Test
    void testApplicationVariableResolverUUID() {
        assertEquals(APP_ID, RESOLVER.doResolve("UUID"));
    }
}

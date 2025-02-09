package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;

class ResolverTestHelper {

    static final Collection<String> TEST_STRINGS = List.of("", " ", "ROOT", "DATA", "abc", "{[()]}²³#", "0", "1", "-1");

    static ParameterConfiguration getParamConfig(String id, String value) {
        var paramConfig = new ParameterConfiguration();
        paramConfig.id = id;
        paramConfig.value = new LinkedValueConfiguration(value);
        return paramConfig;
    }

    static ApplicationConfiguration getAppConfig(String id, String name) {
        return getAppConfig(id, name, Collections.emptyList());
    }

    static ApplicationConfiguration getAppConfig(String id, String name, Collection<ParameterConfiguration> params) {
        var appConfig = new ApplicationConfiguration();
        appConfig.id = id;
        appConfig.name = name;
        appConfig.start = new CommandConfiguration();
        appConfig.start.parameters.addAll(params);
        return appConfig;
    }

    static void testDelegation(PrefixResolver resolver) {
        String msg = "Delegated resolving must return null for " + resolver.getClass().getSimpleName();

        assertNull(resolver.doResolve("APP_NAME_0:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_1:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_2:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_3:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_4:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_5:cool.param"), msg);
        assertNull(resolver.doResolve("APP_NAME_6:cool.param"), msg);

        assertNull(resolver.doResolve("APP_NAME_0:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_1:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_2:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_3:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_4:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_5:set1.id1"), msg);
        assertNull(resolver.doResolve("APP_NAME_6:set1.id1"), msg);
    }
}

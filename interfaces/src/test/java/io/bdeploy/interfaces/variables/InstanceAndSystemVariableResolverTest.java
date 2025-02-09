package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

class InstanceAndSystemVariableResolverTest {

    @Test
    void testInstanceAndSystemVariableResolver() {
        var config = new InstanceNodeConfiguration();
        config.variables.put("key1", "value1");
        config.variables.put("key2", "{{ENV:SOME_VALUE}}");
        config.variables.put("key3", "{{ENV:SOME_VALUE}}");
        config.variables.put("key4", "value4");
        config.variables.put("key5", "{{ENV:SOME_OTHER_VALUE}}");

        var resolver = new InstanceAndSystemVariableResolver(config);
        config.variables.forEach((key, value) -> assertEquals(value, resolver.doResolve(key)));
    }
}

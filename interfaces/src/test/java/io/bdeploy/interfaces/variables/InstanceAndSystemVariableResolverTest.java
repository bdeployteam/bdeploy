package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;

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

    @Test
    void testSystemVariableOverride() {
        var node = new InstanceNodeConfiguration();

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.instanceVariables.add(new VariableConfiguration("instance.var", "instanceVariableValue1"));
        instance.instanceVariables.add(new VariableConfiguration("common.var", "instanceVariableValue2"));

        SystemConfiguration system = new SystemConfiguration();
        system.systemVariables.add(new VariableConfiguration("system.var", "systemVariableValue1"));
        system.systemVariables.add(new VariableConfiguration("common.var", "systemVariableValue2"));

        node.mergeVariables(instance, system, null);

        assertEquals(node.variables.get("instance.var"), "instanceVariableValue1");
        assertEquals(node.variables.get("system.var"), "systemVariableValue1");
        assertEquals(node.variables.get("common.var"), "systemVariableValue2");

        InstanceAndSystemVariableResolver resolver = new InstanceAndSystemVariableResolver(node);
        assertEquals(TemplateHelper.process("{{X:instance.var}}", resolver), "instanceVariableValue1");
        assertEquals(TemplateHelper.process("{{X:system.var}}", resolver), "systemVariableValue1");
        assertEquals(TemplateHelper.process("{{X:common.var}}", resolver), "systemVariableValue2");
    }
}

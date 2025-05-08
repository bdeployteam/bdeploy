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

        assertEquals("instanceVariableValue1", node.variables.get("instance.var"));
        assertEquals("systemVariableValue1", node.variables.get("system.var"));
        assertEquals("systemVariableValue2", node.variables.get("common.var"));

        InstanceAndSystemVariableResolver resolver = new InstanceAndSystemVariableResolver(node);
        assertEquals("instanceVariableValue1", TemplateHelper.process("{{X:instance.var}}", resolver));
        assertEquals("systemVariableValue1", TemplateHelper.process("{{X:system.var}}", resolver));
        assertEquals("systemVariableValue2", TemplateHelper.process("{{X:common.var}}", resolver));
    }
}

package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;

class FixedParameterListValueResolverTest {

    @Test
    void testFixedParameterListValueResolver() {
        var paramSet = new ArrayList<ParameterConfiguration>();
        paramSet.add(ResolverTestHelper.getParamConfig("id1", "value1"));
        paramSet.add(ResolverTestHelper.getParamConfig("id2", "{{ENV:SOME_VALUE}}"));
        paramSet.add(ResolverTestHelper.getParamConfig("id3", "{{ENV:SOME_VALUE}}"));
        paramSet.add(ResolverTestHelper.getParamConfig("id4", "value4"));
        paramSet.add(ResolverTestHelper.getParamConfig("id5", "{{ENV:SOME_OTHER_VALUE}}"));

        var resolver = new FixedParameterListValueResolver(paramSet);

        // Test normal value retrieval
        assertEquals("value1", resolver.doResolve("id1"));
        assertEquals("value4", resolver.doResolve("id4"));

        // Test linkExpression retrieval
        assertEquals("{{ENV:SOME_VALUE}}", resolver.doResolve("id2"));
        assertEquals("{{ENV:SOME_VALUE}}", resolver.doResolve("id3"));
        assertEquals("{{ENV:SOME_OTHER_VALUE}}", resolver.doResolve("id5"));

        // Test delegation
        ResolverTestHelper.testDelegation(resolver);
    }
}

package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

class ParameterValueResolverTest {

    @Test
    void testParameterValueResolver() {
        var paramSet1 = new ArrayList<ParameterConfiguration>();
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id1", "set1_value1"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id2", "set1_value2"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id2", "set1_value3"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id4", "set1_value4"));

        var paramSet2 = new ArrayList<ParameterConfiguration>();
        paramSet2.add(ResolverTestHelper.getParamConfig("set2.id1", "set2_value1"));
        paramSet2.add(ResolverTestHelper.getParamConfig("set2.id2", "set2_value2"));
        paramSet2.add(ResolverTestHelper.getParamConfig("set2.id3", "set2_value3"));
        paramSet2.add(ResolverTestHelper.getParamConfig("set2.id4", "set2_value4"));

        var paramSet3 = new ArrayList<ParameterConfiguration>();
        paramSet3.add(ResolverTestHelper.getParamConfig("set3.id1", "set3_value1"));
        paramSet3.add(ResolverTestHelper.getParamConfig("set3.id2", "set3_value2"));

        var paramSet4 = new ArrayList<ParameterConfiguration>();
        paramSet4.add(ResolverTestHelper.getParamConfig("set4.id1", "set4_value1"));
        paramSet4.add(ResolverTestHelper.getParamConfig("set4.id2", "{{ENV:SOME_VALUE}}"));
        paramSet4.add(ResolverTestHelper.getParamConfig("set4.id3", "{{ENV:SOME_VALUE}}"));
        paramSet4.add(ResolverTestHelper.getParamConfig("set4.id4", "set4_value2"));
        paramSet4.add(ResolverTestHelper.getParamConfig("set4.id5", "{{ENV:SOME_OTHER_VALUE}}"));

        var config = new InstanceNodeConfiguration();
        config.name = "Human readable name of the instance";
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_1", "APP_NAME_1", Collections.emptyList()));
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_2", "APP_NAME_2", paramSet1));
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_3", "APP_NAME_3", paramSet2));
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_4", "APP_NAME_4", paramSet2));
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_5", "APP_NAME_5", paramSet3));
        config.applications.add(ResolverTestHelper.getAppConfig("APP_ID_6", "APP_NAME_6", paramSet4));

        var resolver = new ParameterValueResolver(new ApplicationParameterProvider(config));

        // Test if null is returned whenever no ':' is in the given parameter
        assertNull(resolver.doResolve(""));
        assertNull(resolver.doResolve(" "));
        assertNull(resolver.doResolve("abc"));
        assertNull(resolver.doResolve("0"));
        assertNull(resolver.doResolve("1"));
        assertNull(resolver.doResolve("-1"));

        // Test unknown application
        assertNull(resolver.doResolve("APP_NAME_0:unknown.param"));
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_0:" + param.id)));
        paramSet2.forEach(param -> assertNull(resolver.doResolve("APP_NAME_0:" + param.id)));
        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_0:" + param.id)));
        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_0:" + param.id)));

        // Test unknown parameters
        assertNull(resolver.doResolve("APP_NAME_1:unknown.param"));
        assertNull(resolver.doResolve("APP_NAME_2:unknown.param"));
        assertNull(resolver.doResolve("APP_NAME_3:unknown.param"));
        assertNull(resolver.doResolve("APP_NAME_4:unknown.param"));
        assertNull(resolver.doResolve("APP_NAME_5:unknown.param"));
        assertNull(resolver.doResolve("APP_NAME_6:unknown.param"));

        // Test resolving parameters that exist only in other applications of the same node
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_1:" + param.id)));
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_3:" + param.id)));
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_4:" + param.id)));
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_5:" + param.id)));
        paramSet1.forEach(param -> assertNull(resolver.doResolve("APP_NAME_5:" + param.id)));

        paramSet2.forEach(param -> assertNull(resolver.doResolve("APP_NAME_1:" + param.id)));
        paramSet2.forEach(param -> assertNull(resolver.doResolve("APP_NAME_2:" + param.id)));
        paramSet2.forEach(param -> assertNull(resolver.doResolve("APP_NAME_5:" + param.id)));
        paramSet2.forEach(param -> assertNull(resolver.doResolve("APP_NAME_6:" + param.id)));

        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_1:" + param.id)));
        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_2:" + param.id)));
        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_3:" + param.id)));
        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_4:" + param.id)));
        paramSet3.forEach(param -> assertNull(resolver.doResolve("APP_NAME_6:" + param.id)));

        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_1:" + param.id)));
        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_2:" + param.id)));
        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_3:" + param.id)));
        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_4:" + param.id)));
        paramSet4.forEach(param -> assertNull(resolver.doResolve("APP_NAME_5:" + param.id)));

        // Test duplicate parameter ID
        assertNull(resolver.doResolve("APP_ID_2:set1.id2"), "Resolving a duplicate parameter must always return null");

        // Test normal value retrieval
        assertEquals("set1_value1", resolver.doResolve("APP_NAME_2:set1.id1"));
        assertEquals("set1_value4", resolver.doResolve("APP_NAME_2:set1.id4"));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver.doResolve("APP_NAME_3:" + param.id)));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver.doResolve("APP_NAME_4:" + param.id)));
        paramSet3.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver.doResolve("APP_NAME_5:" + param.id)));
        assertEquals("set4_value1", resolver.doResolve("APP_NAME_6:set4.id1"));
        assertEquals("set4_value2", resolver.doResolve("APP_NAME_6:set4.id4"));

        // Test linkExpression retrieval
        assertEquals("{{ENV:SOME_VALUE}}", resolver.doResolve("APP_NAME_6:set4.id2"));
        assertEquals("{{ENV:SOME_VALUE}}", resolver.doResolve("APP_NAME_6:set4.id3"));
        assertEquals("{{ENV:SOME_OTHER_VALUE}}", resolver.doResolve("APP_NAME_6:set4.id5"));
    }
}

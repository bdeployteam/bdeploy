package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

class ApplicationParameterValueResolverTest {

    @Test
    void testApplicationParameterValueResolver() {
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

        var resolver1 = new ApplicationParameterValueResolver("APP_ID_0", config); // Unknown application ID
        var resolver2 = new ApplicationParameterValueResolver("APP_ID_1", config); // No parameters
        var resolver3 = new ApplicationParameterValueResolver("APP_ID_2", config); // paramSet1 -> duplicate parameter ID "set1_id2"
        var resolver4 = new ApplicationParameterValueResolver("APP_ID_3", config); // paramSet2 -> same as APP_ID_4
        var resolver5 = new ApplicationParameterValueResolver("APP_ID_4", config); // paramSet2 -> same as APP_ID_3
        var resolver6 = new ApplicationParameterValueResolver("APP_ID_5", config); // paramSet3 -> without variable expansions
        var resolver7 = new ApplicationParameterValueResolver("APP_ID_6", config); // paramSet4 -> with variable expansions

        // Test unknown application
        assertNull(resolver1.doResolve("unknown app"),
                "Attempting to resolve a parameter of a non-existant application must always return null");

        // Test unknown parameters
        assertNull(resolver1.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver2.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver3.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver4.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver5.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver6.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");
        assertNull(resolver7.doResolve("unknown param"), "Resolving a non-existant parameter must always return null");

        // Test resolving parameters that exist only in other applications of the same node
        paramSet1.forEach(param -> assertNull(resolver2.doResolve(param.id)));
        paramSet2.forEach(param -> assertNull(resolver2.doResolve(param.id)));
        paramSet3.forEach(param -> assertNull(resolver2.doResolve(param.id)));
        paramSet4.forEach(param -> assertNull(resolver2.doResolve(param.id)));

        paramSet2.forEach(param -> assertNull(resolver3.doResolve(param.id)));
        paramSet3.forEach(param -> assertNull(resolver3.doResolve(param.id)));
        paramSet4.forEach(param -> assertNull(resolver3.doResolve(param.id)));

        paramSet1.forEach(param -> assertNull(resolver4.doResolve(param.id)));
        paramSet3.forEach(param -> assertNull(resolver4.doResolve(param.id)));
        paramSet4.forEach(param -> assertNull(resolver4.doResolve(param.id)));

        paramSet1.forEach(param -> assertNull(resolver5.doResolve(param.id)));
        paramSet3.forEach(param -> assertNull(resolver5.doResolve(param.id)));
        paramSet4.forEach(param -> assertNull(resolver5.doResolve(param.id)));

        paramSet1.forEach(param -> assertNull(resolver6.doResolve(param.id)));
        paramSet2.forEach(param -> assertNull(resolver6.doResolve(param.id)));
        paramSet4.forEach(param -> assertNull(resolver6.doResolve(param.id)));

        paramSet1.forEach(param -> assertNull(resolver7.doResolve(param.id)));
        paramSet2.forEach(param -> assertNull(resolver7.doResolve(param.id)));
        paramSet3.forEach(param -> assertNull(resolver7.doResolve(param.id)));

        // Test duplicate parameter ID
        assertNull(resolver3.doResolve("set1_id2"), "Resolving a duplicate parameter must always return null");

        // Test normal value retrieval
        assertEquals("set1_value1", resolver3.doResolve("set1.id1"));
        assertEquals("set1_value4", resolver3.doResolve("set1.id4"));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver4.doResolve(param.id)));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver5.doResolve(param.id)));
        paramSet3.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver6.doResolve(param.id)));
        assertEquals("set4_value1", resolver7.doResolve("set4.id1"));
        assertEquals("set4_value2", resolver7.doResolve("set4.id4"));

        // Test linkExpression retrieval
        assertEquals("{{ENV:SOME_VALUE}}", resolver7.doResolve("set4.id2"));
        assertEquals("{{ENV:SOME_VALUE}}", resolver7.doResolve("set4.id3"));
        assertEquals("{{ENV:SOME_OTHER_VALUE}}", resolver7.doResolve("set4.id5"));

        // Test value retrieval delegation
        ResolverTestHelper.testDelegation(resolver1);
        ResolverTestHelper.testDelegation(resolver2);
        ResolverTestHelper.testDelegation(resolver3);
        ResolverTestHelper.testDelegation(resolver4);
        ResolverTestHelper.testDelegation(resolver5);
        ResolverTestHelper.testDelegation(resolver6);
        ResolverTestHelper.testDelegation(resolver7);
    }
}

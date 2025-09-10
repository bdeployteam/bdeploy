package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

class CompositeResolverTest {

    private static final String ITEM_ID = "id";
    private static final String ITEM_TAG = "1";

    private static final Path DEPLOYMENT_DIR = Path.of("deployment").toAbsolutePath();
    private static final Path APP_DIR = DEPLOYMENT_DIR.resolve(ITEM_ID);
    private static final Path DATA_DIR = APP_DIR.resolve("data");
    private static final Path BIN_DIR = APP_DIR.resolve("bin").resolve(ITEM_TAG);
    private static final Path CONFIG_DIR = BIN_DIR.resolve("config");
    private static final Path RUNTIME_DIR = BIN_DIR.resolve("runtime");
    private static final Path INSTANCE_MANIFEST_POOL_DIR = APP_DIR.resolve("pool");
    private static final Path MANIFEST_POOL_DIR = DEPLOYMENT_DIR.resolve("pool");
    private static final Path LOG_DATA_DIR = Path.of("log_data").toAbsolutePath();

    private static final String ROOT_DIR_STRING = APP_DIR.toString();
    private static final String DATA_DIR_STRING = DATA_DIR.toString();
    private static final String BIN_DIR_STRING = BIN_DIR.toString();
    private static final String CONFIG_DIR_STRING = CONFIG_DIR.toString();
    private static final String RUNTIME_DIR_STRING = RUNTIME_DIR.toString();
    private static final String INSTANCE_MANIFEST_POOL_DIR_STRING = INSTANCE_MANIFEST_POOL_DIR.toString();
    private static final String MANIFEST_POOL_DIR_STRING = MANIFEST_POOL_DIR.toString();

    private static final DeploymentPathProvider DPP = new DeploymentPathProvider(DEPLOYMENT_DIR, LOG_DATA_DIR, ITEM_ID, ITEM_TAG);

    @Test
    void testCompositeResolver() {
        var paramSet1 = new ArrayList<ParameterConfiguration>();
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id1", "set1_value1"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id2", "set1_value2"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id2", "set1_value3"));
        paramSet1.add(ResolverTestHelper.getParamConfig("set1.id4", "set1_value4"));

        var paramSet2 = new ArrayList<ParameterConfiguration>();
        paramSet2.add(ResolverTestHelper.getParamConfig("set2.id1", "false"));
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

        var appConfig1 = ResolverTestHelper.getAppConfig("APP_ID_1", "APP_NAME_1", Collections.emptyList());
        var appConfig2 = ResolverTestHelper.getAppConfig("APP_ID_2", "APP_NAME_2", paramSet1);
        var appConfig3 = ResolverTestHelper.getAppConfig("APP_ID_3", "APP_NAME_3", paramSet2);
        var appConfig4 = ResolverTestHelper.getAppConfig("APP_ID_4", "APP_NAME_4", paramSet2);
        var appConfig5 = ResolverTestHelper.getAppConfig("APP_ID_5", "APP_NAME_5", paramSet3);
        var appConfig6 = ResolverTestHelper.getAppConfig("APP_ID_6", "APP_NAME_6", paramSet4);
        appConfig1.application = new Manifest.Key("key1", "1");
        appConfig2.application = new Manifest.Key("key2", "1");
        appConfig3.application = new Manifest.Key("key3", "1");
        appConfig4.application = new Manifest.Key("key4", "1");
        appConfig5.application = new Manifest.Key("key5", "1");
        appConfig6.application = new Manifest.Key("key6", "1");

        var nodeConfig = new InstanceNodeConfiguration();
        nodeConfig.purpose = InstancePurpose.TEST;
        nodeConfig.id = "nodeConfigId";
        nodeConfig.name = "Human readable name of the node config";
        nodeConfig.product = new Manifest.Key("test/product", "1");
        nodeConfig.applications.add(appConfig1);
        nodeConfig.applications.add(appConfig2);
        nodeConfig.applications.add(appConfig3);
        nodeConfig.applications.add(appConfig4);
        nodeConfig.applications.add(appConfig5);
        nodeConfig.applications.add(appConfig6);

        var clientCfg1 = new ClientApplicationConfiguration();
        var clientCfg2 = new ClientApplicationConfiguration();
        var clientCfg3 = new ClientApplicationConfiguration();
        var clientCfg4 = new ClientApplicationConfiguration();
        var clientCfg5 = new ClientApplicationConfiguration();
        var clientCfg6 = new ClientApplicationConfiguration();
        clientCfg1.appConfig = appConfig1;
        clientCfg2.appConfig = appConfig2;
        clientCfg3.appConfig = appConfig3;
        clientCfg4.appConfig = appConfig4;
        clientCfg5.appConfig = appConfig5;
        clientCfg6.appConfig = appConfig6;
        clientCfg1.instanceConfig = nodeConfig;
        clientCfg2.instanceConfig = nodeConfig;
        clientCfg3.instanceConfig = nodeConfig;
        clientCfg4.instanceConfig = nodeConfig;
        clientCfg5.instanceConfig = nodeConfig;
        clientCfg6.instanceConfig = nodeConfig;
        clientCfg1.activeTag = "activeTag1";
        clientCfg2.activeTag = "activeTag2";
        clientCfg3.activeTag = "activeTag3";
        clientCfg4.activeTag = "activeTag4";
        clientCfg5.activeTag = "activeTag5";
        clientCfg6.activeTag = "activeTag6";

        var resolver1 = getResolver(clientCfg1);
        var resolver2 = getResolver(clientCfg2);
        var resolver3 = getResolver(clientCfg3);
        var resolver4 = getResolver(clientCfg4);
        var resolver5 = getResolver(clientCfg5);
        var resolver6 = getResolver(clientCfg6);
        var resolvers = Set.of(resolver1, resolver2, resolver3, resolver4, resolver5, resolver6)
                .toArray(CompositeResolver[]::new);

        // Test parameters without prefix
        testParametersWithoutPrefix(resolvers);

        // Test unknown parameters
        String envVar = Variables.ENVIRONMENT_VARIABLE.getPrefix() + "unknown.param";
        Arrays.stream(resolvers).forEach(resolver -> assertEquals("", resolver.apply(envVar)));
        Arrays.stream(Variables.values())//
                .filter(variable -> variable != Variables.ENVIRONMENT_VARIABLE)//
                .map(Variables::getPrefix)//
                .map(prefix -> prefix + "unknown.param")//
                .forEach(param -> Arrays.stream(resolvers).forEach(resolver -> assertNull(resolver.apply(param))));

        // Test resolving parameters that exist only in other applications of the same node
        testResolvingParamsWithoutProperReference(paramSet1, resolver1, resolver3, resolver4, resolver5, resolver6);
        testResolvingParamsWithoutProperReference(paramSet2, resolver1, resolver2, resolver5, resolver6);
        testResolvingParamsWithoutProperReference(paramSet3, resolver1, resolver2, resolver3, resolver4, resolver6);
        testResolvingParamsWithoutProperReference(paramSet4, resolver1, resolver2, resolver3, resolver4, resolver5);

        // Test duplicate parameter ID
        assertNull(resolver2.apply("V:set1_id2"), "Resolving a duplicate parameter must always return null");

        // Test normal parameter value retrieval
        resolver2.apply("V:set1.id1");
        assertEquals("set1_value1", resolver2.apply("V:set1.id1"));
        assertEquals("set1_value4", resolver2.apply("V:set1.id4"));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver3.apply("V:" + param.id)));
        paramSet2.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver4.apply("V:" + param.id)));
        paramSet3.forEach(param -> assertEquals(param.value.getPreRenderable(), resolver5.apply("V:" + param.id)));
        assertEquals("set4_value1", resolver6.apply("V:set4.id1"));
        assertEquals("set4_value2", resolver6.apply("V:set4.id4"));

        // Test linkExpression retrieval
        assertEquals("{{ENV:SOME_VALUE}}", resolver6.apply("V:set4.id2"));
        assertEquals("{{ENV:SOME_VALUE}}", resolver6.apply("V:set4.id3"));
        assertEquals("{{ENV:SOME_OTHER_VALUE}}", resolver6.apply("V:set4.id5"));

        // Test delegation
        testDelegationOfUnknownParam(resolvers);
        assertEquals("set1_value1", resolver1.apply("V:APP_NAME_2:set1.id1"));
        assertNull(resolver1.apply("V:APP_NAME_2:set1.id2"));
        assertEquals("set1_value4", resolver1.apply("V:APP_NAME_2:set1.id4"));
        testValidDelegation(paramSet2, "APP_NAME_3", resolvers);
        testValidDelegation(paramSet2, "APP_NAME_4", resolvers);
        testValidDelegation(paramSet3, "APP_NAME_5", resolvers);
        testValidDelegation(paramSet4, "APP_NAME_6", resolvers);

        // Test application variables
        assertEquals("APP_ID_1", resolver1.apply("A:ID"));
        assertEquals("APP_ID_2", resolver2.apply("A:ID"));
        assertEquals("APP_ID_3", resolver3.apply("A:ID"));
        assertEquals("APP_ID_4", resolver4.apply("A:ID"));
        assertEquals("APP_ID_5", resolver5.apply("A:ID"));
        assertEquals("APP_ID_6", resolver6.apply("A:ID"));
        assertEquals("APP_NAME_1", resolver1.apply("A:NAME"));
        assertEquals("APP_NAME_2", resolver2.apply("A:NAME"));
        assertEquals("APP_NAME_3", resolver3.apply("A:NAME"));
        assertEquals("APP_NAME_4", resolver4.apply("A:NAME"));
        assertEquals("APP_NAME_5", resolver5.apply("A:NAME"));
        assertEquals("APP_NAME_6", resolver6.apply("A:NAME"));

        // Test conditional expressions
        Arrays.stream(resolvers).forEach(resolver -> {
            assertEquals("foo", resolver.apply("IF:V:APP_NAME_2:set1.id1?foo:bar")); // contains some String other than "false"
            assertEquals("bar", resolver.apply("IF:V:APP_NAME_3:set2.id1?foo:bar")); // contains the String "false"
            assertEquals("bar", resolver.apply("IF:V:APP_NAME_4:set0.id0?foo:bar")); // does not exist -> is null

        });

        // Test deployment path resolution error cases
        Arrays.stream(resolvers).forEach(resolver -> assertNull(resolver.apply("P:UNKNOWN_DIRECTORY")));

        // Test deployment path resolution happy cases
        Arrays.stream(resolvers).forEach(resolver -> {
            assertEquals(ROOT_DIR_STRING, resolver.apply("P:ROOT"));
            assertEquals(DATA_DIR_STRING, resolver.apply("P:DATA"));
            assertEquals(BIN_DIR_STRING, resolver.apply("P:BIN"));
            assertEquals(CONFIG_DIR_STRING, resolver.apply("P:CONFIG"));
            assertEquals(RUNTIME_DIR_STRING, resolver.apply("P:RUNTIME"));
            assertEquals(INSTANCE_MANIFEST_POOL_DIR_STRING, resolver.apply("P:INSTANCE_MANIFEST_POOL"));
            assertEquals(MANIFEST_POOL_DIR_STRING, resolver.apply("P:MANIFEST_POOL"));
            assertEquals(LOG_DATA_DIR.resolve(ITEM_ID).toString(), resolver.apply("P:LOG_DATA"));
        });

        // Test instance variable resolution
        Arrays.stream(resolvers).forEach(resolver -> {
            assertNull(resolver.apply("I:UNKNOWN_VARIABLE"));
            assertEquals(InstancePurpose.TEST.name(), resolver.apply("I:SYSTEM_PURPOSE"));
            assertEquals("nodeConfigId", resolver.apply("I:ID"));
            assertEquals("Human readable name of the node config", resolver.apply("I:NAME"));
            assertEquals("test/product", resolver.apply("I:PRODUCT_ID"));
            assertEquals("1", resolver.apply("I:PRODUCT_TAG"));
            assertEquals(APP_DIR.resolve(InstanceDeploymentInformationApi.FILE_NAME).toString(),
                    resolver.apply("I:DEPLOYMENT_INFO_FILE"));
        });
        assertEquals("activeTag1", resolver1.apply("I:TAG"));
        assertEquals("activeTag2", resolver2.apply("I:TAG"));
        assertEquals("activeTag3", resolver3.apply("I:TAG"));
        assertEquals("activeTag4", resolver4.apply("I:TAG"));
        assertEquals("activeTag5", resolver5.apply("I:TAG"));
        assertEquals("activeTag6", resolver6.apply("I:TAG"));

        // Test escaping characters
        Arrays.stream(resolvers).forEach(resolver -> {
            assertEquals("\"Human readable name of the node config\"", resolver.apply("YAML:I:NAME"));
            assertEquals("Human readable name of the node config", resolver.apply("XML:I:NAME"));
            assertEquals("Human readable name of the node config", resolver.apply("JSON:I:NAME"));
        });

    }

    private static CompositeResolver getResolver(ClientApplicationConfiguration clientCfg) {
        Key applicationKey = clientCfg.appConfig.application;
        Map<Key, Path> pooledSoftware = new HashMap<>();
        pooledSoftware.put(applicationKey, MANIFEST_POOL_DIR.resolve(applicationKey.directoryFriendlyName()));
        for (Manifest.Key key : clientCfg.resolvedRequires) {
            pooledSoftware.put(key, MANIFEST_POOL_DIR.resolve(key.directoryFriendlyName()));
        }

        var preResolver = Resolvers.forInstance(clientCfg.instanceConfig, clientCfg.activeTag, DPP);
        preResolver.add(new ManifestVariableResolver(new ManifestRefPathProvider(pooledSoftware)));
        preResolver.add(new LocalHostnameResolver(true));

        return Resolvers.forApplication(preResolver, clientCfg.instanceConfig, clientCfg.appConfig);
    }

    private static void testParametersWithoutPrefix(CompositeResolver... resolvers) {
        for (var resolver : resolvers) {
            assertNull(resolver.apply("no.prefix.param"), "Resolving a non-existant parameter must always return null");
        }
    }

    private static void testResolvingParamsWithoutProperReference(List<ParameterConfiguration> paramSet,
            CompositeResolver... compositeResolvers) {
        for (var resolver : compositeResolvers) {
            paramSet.forEach(param -> assertNull(resolver.apply(param.id)));
        }
    }

    private static void testDelegationOfUnknownParam(CompositeResolver... resolvers) {
        String testValue = "V:APP_NAME_1:some.param";
        for (var resolver : resolvers) {
            assertNull(resolver.apply(testValue));
        }
    }

    private static void testValidDelegation(List<ParameterConfiguration> paramSet, String appName,
            CompositeResolver... resolvers) {
        for (var resolver : resolvers) {
            paramSet.forEach(param -> {
                System.out.println(param.value.getPreRenderable());
                System.out.println(appName + ':' + param.id);
                assertEquals(param.value.getPreRenderable(), resolver.apply("V:" + appName + ':' + param.id));
            });
        }
    }
}

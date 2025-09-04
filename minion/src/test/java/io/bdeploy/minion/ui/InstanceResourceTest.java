package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.NodeAttachDto;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(TestMinion.class)
class InstanceResourceTest {

    void addNodes(RemoteService remote) {
        NodeManagementResource nmr = ResourceProvider.getResource(remote, NodeManagementResource.class, null);

        NodeAttachDto dto = new NodeAttachDto();
        dto.remote = remote;
        dto.sourceMode = MinionMode.NODE;

        dto.name = "Node1";
        nmr.addServerNode(dto);
        dto.name = "Node2";
        nmr.addServerNode(dto);
        dto.name = "Node3";
        nmr.addServerNode(dto);
    }

    /**
     * Groups the given node descriptions by the target node name
     */
    private static Map<String, InstanceNodeConfigurationDto> groupByNode(Collection<InstanceNodeConfigurationDto> values) {
        Map<String, InstanceNodeConfigurationDto> map = new HashMap<>();
        for (InstanceNodeConfigurationDto value : values) {
            map.put(value.nodeName, value);
        }
        return map;
    }

    @Test
    void testGetConfiguration(InstanceGroupResource root, @TempDir Path tmpDir, RemoteService remote) throws Exception {
        addNodes(remote);

        // Prepare and push group
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);
        InstanceResource instanceResource = root.getInstanceResource(group.name);

        // Prepare and push product
        ProductManifest product = TestFactory.pushProduct(group.name, remote, tmpDir);

        // Push instances and their configuration
        createInstance(remote, tmpDir, group.name, product, "Instance1", "Node1", "Node2");
        createInstance(remote, tmpDir, group.name, product, "Instance2", "Node2");
        createInstance(remote, tmpDir, group.name, product, "Instance3", "Node3", Minion.DEFAULT_NAME);

        // Verify Instance1
        InstanceNodeConfigurationListDto instanceConfig = instanceResource.getNodeConfigurations("Instance1", "1");
        List<InstanceNodeConfigurationDto> nodeConfigs = instanceConfig.nodeConfigDtos;
        Map<String, InstanceNodeConfigurationDto> node2NodeDto = groupByNode(nodeConfigs);
        assertEquals(2, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), true);
        verifyNodeDto(node2NodeDto.get("Node2"), true);
        verifyNodeDto(node2NodeDto.get("Node3"), false);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), false);

        // Verify Instance2
        instanceConfig = instanceResource.getNodeConfigurations("Instance2", "1");
        nodeConfigs = instanceConfig.nodeConfigDtos;
        node2NodeDto = groupByNode(nodeConfigs);
        assertEquals(1, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), false);
        verifyNodeDto(node2NodeDto.get("Node2"), true);
        verifyNodeDto(node2NodeDto.get("Node3"), false);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), false);

        // Verify Instance3
        instanceConfig = instanceResource.getNodeConfigurations("Instance3", "1");
        nodeConfigs = instanceConfig.nodeConfigDtos;
        node2NodeDto = groupByNode(nodeConfigs);
        assertEquals(2, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), false);
        verifyNodeDto(node2NodeDto.get("Node2"), false);
        verifyNodeDto(node2NodeDto.get("Node3"), true);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), true);
    }

    @Test
    void testUpdateConfiguration(InstanceGroupResource root, RemoteService remote, @TempDir Path tmpDir) throws Exception {
        addNodes(remote);

        // Prepare and push group
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);
        InstanceResource instanceResource = root.getInstanceResource(group.name);

        // Prepare and push product
        ProductManifest product = TestFactory.pushProduct(group.name, remote, tmpDir);

        // Prepare and push instance
        InstanceConfiguration instanceConfig = TestFactory.createInstanceConfig("DemoInstance", product);
        instanceConfig.autoStart = true;
        instanceResource.create(instanceConfig, null);

        // Create node configuration
        InstanceNodeConfigurationDto nodeDto = new InstanceNodeConfigurationDto("Node1", null);
        {
            InstanceNodeConfiguration nodeConfig = new InstanceNodeConfiguration();
            nodeConfig.applications.add(TestFactory.createAppConfig(product));
            nodeConfig.id = instanceConfig.id;
            nodeConfig.autoStart = true;
            // wrong name (intentionally). the backend must correct the name.
            nodeConfig.name = "DemoInstance-Node1-Config";
            nodeDto.nodeConfiguration = nodeConfig;
        }
        instanceResource.update("DemoInstance",
                new InstanceUpdateDto(new InstanceConfigurationDto(instanceConfig, Collections.singletonList(nodeDto)), null),
                null, "1");

        // Check node configuration
        InstanceNodeConfigurationListDto instanceConfigDto = instanceResource.getNodeConfigurations("DemoInstance", "2");
        List<InstanceNodeConfigurationDto> availableNodeConfigs = instanceConfigDto.nodeConfigDtos;
        Map<String, InstanceNodeConfigurationDto> node2Config = groupByNode(availableNodeConfigs);
        assertEquals(1, node2Config.size()); // one for each node available

        // Master should not have a configuration
        verifyNodeDto(node2Config.get(Minion.DEFAULT_NAME), false);

        // Node1 should have a configuration
        verifyNodeDto(node2Config.get("Node1"), true);

        // Verify configuration
        InstanceNodeConfiguration node1Config = node2Config.get("Node1").nodeConfiguration;
        assertEquals(1, node1Config.applications.size());
        assertTrue(node1Config.autoStart);
        assertEquals("DemoInstance", node1Config.name);

        // Remove all applications from Node1
        nodeDto.nodeConfiguration.applications.clear();
        instanceResource.update("DemoInstance",
                new InstanceUpdateDto(new InstanceConfigurationDto(instanceConfig, Collections.singletonList(nodeDto)), null),
                null, "2");

        // Check the updated configuration
        instanceConfigDto = instanceResource.getNodeConfigurations("DemoInstance", "3");
        availableNodeConfigs = instanceConfigDto.nodeConfigDtos;
        node2Config = groupByNode(availableNodeConfigs);

        // Master should not have a configuration
        verifyNodeDto(node2Config.get(Minion.DEFAULT_NAME), false);
    }

    /** Creates a new instance within the given group. A new node config is created for each passed node name */
    private static void createInstance(RemoteService remote, Path tmp, String groupName, ProductManifest product,
            String instanceName, String... nodeNames) {
        InstanceConfiguration instanceConfig = TestFactory.createInstanceConfig(instanceName, product);
        try (BHive hive = new BHive(tmp.resolve("hive").toUri(), null, new ActivityReporter.Null())) {
            PushOperation pushOperation = new PushOperation();
            Builder instanceManifest = new InstanceManifest.Builder().setInstanceConfiguration(instanceConfig);

            for (String nodeName : nodeNames) {
                InstanceNodeConfiguration nodeConfig = new InstanceNodeConfiguration();
                nodeConfig.id = UuidHelper.randomId();
                nodeConfig.applications.add(TestFactory.createAppConfig(product));

                Key instanceNodeKey = new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(nodeConfig)
                        .setMinionName(nodeName).insert(hive);
                instanceManifest.addInstanceNodeManifest(nodeName, instanceNodeKey);
                pushOperation.addManifest(instanceNodeKey);
            }

            Key instanceKey = instanceManifest.insert(hive);
            pushOperation.addManifest(instanceKey);
            hive.execute(pushOperation.setHiveName(groupName).setRemote(remote));
        }
    }

    /** Verifies that the own configuration as well as the foreign configuration is as expected */
    private static void verifyNodeDto(InstanceNodeConfigurationDto nodeDto, boolean hasConfig) {
        if (hasConfig) {
            assertNotNull(nodeDto.nodeConfiguration);
        } else {
            assertNull(nodeDto);
        }
    }

    @Test
    void testCrud(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        addNodes(remote);

        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.id = root.createId(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        assertEquals(1, res.list().size());

        InstanceConfiguration read = res.read(instance.id).instanceConfiguration;
        assertEquals(product, read.product);
        assertEquals(instance.id, read.id);
        assertEquals("My Instance", read.name);
        assertEquals(InstancePurpose.PRODUCTIVE, read.purpose);

        read.name = "New Desc";
        res.update(instance.id, new InstanceUpdateDto(new InstanceConfigurationDto(read, null), null), null, "1");

        InstanceConfiguration reread = res.read(instance.id).instanceConfiguration;
        assertEquals("New Desc", reread.name);

        res.delete(instance.id);
        assertTrue(res.list().isEmpty());
    }

    @Test
    void testMultipleVersions(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        addNodes(remote);

        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.id = root.createId(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        instance.name = "My modified Instance";
        res.update(instance.id, new InstanceUpdateDto(new InstanceConfigurationDto(instance, null), null), null, "1");

        InstanceConfiguration read = res.read(instance.id).instanceConfiguration;
        assertEquals(read.name, instance.name);

        List<InstanceVersionDto> listVersions = res.listVersions(instance.id);
        assertEquals(2, listVersions.size());
        assertEquals("1", listVersions.get(0).key.getTag());
        assertEquals("2", listVersions.get(1).key.getTag());

        InstanceConfiguration readVersion = res.readVersion(instance.id, "1");
        assertEquals("My Instance", readVersion.name);

        assertThrows(NotFoundException.class, () -> res.readVersion(instance.id, "3"));
    }

    @Test
    void testCheckPortStates(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws Exception {
        addNodes(remote);

        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.id = root.createId(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        int port = 0;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            port = ss.getLocalPort();
        }

        // it is free now
        Map<Integer, Boolean> portStates = root.getInstanceResource("demo").getPortStates(instance.id, "master",
                Collections.singletonList(port));
        assertEquals(1, portStates.size());
        assertEquals(Boolean.FALSE, portStates.get(port));

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            portStates = root.getInstanceResource("demo").getPortStates(instance.id, "master", Collections.singletonList(port));
            assertEquals(1, portStates.size());
            assertEquals(Boolean.TRUE, portStates.get(port));
        }
    }

}

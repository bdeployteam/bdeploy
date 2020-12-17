package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestMinion.class)
public class InstanceResourceTest {

    @BeforeEach
    void addNodes(MinionRoot mr) {
        BHive hive = mr.getHive();
        MinionManifest mf = new MinionManifest(hive);

        MinionConfiguration config = mf.read();
        config.addMinion("Node1", MinionDto.create(false, config.getRemote("master")));
        config.addMinion("Node2", MinionDto.create(false, config.getRemote("master")));
        config.addMinion("Node3", MinionDto.create(false, config.getRemote("master")));

        mf.update(config);
    }

    @Test
    void getConfiguration(InstanceGroupResource root, @TempDir Path tmpDir, RemoteService remote) throws Exception {
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
        Map<String, InstanceNodeConfigurationDto> node2NodeDto = InstanceNodeConfigurationDto.groupByNode(nodeConfigs);
        assertEquals(4, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), true);
        verifyNodeDto(node2NodeDto.get("Node2"), true);
        verifyNodeDto(node2NodeDto.get("Node3"), false);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), false);

        // Verify Instance2
        instanceConfig = instanceResource.getNodeConfigurations("Instance2", "1");
        nodeConfigs = instanceConfig.nodeConfigDtos;
        node2NodeDto = InstanceNodeConfigurationDto.groupByNode(nodeConfigs);
        assertEquals(4, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), false);
        verifyNodeDto(node2NodeDto.get("Node2"), true);
        verifyNodeDto(node2NodeDto.get("Node3"), false);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), false);

        // Verify Instance3
        instanceConfig = instanceResource.getNodeConfigurations("Instance3", "1");
        nodeConfigs = instanceConfig.nodeConfigDtos;
        node2NodeDto = InstanceNodeConfigurationDto.groupByNode(nodeConfigs);
        assertEquals(4, node2NodeDto.size());
        verifyNodeDto(node2NodeDto.get("Node1"), false);
        verifyNodeDto(node2NodeDto.get("Node2"), false);
        verifyNodeDto(node2NodeDto.get("Node3"), true);
        verifyNodeDto(node2NodeDto.get(Minion.DEFAULT_NAME), true);
    }

    @Test
    void updateConfiguration(InstanceGroupResource root, RemoteService remote, @TempDir Path tmpDir) throws Exception {
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
        InstanceNodeConfigurationDto nodeDto = new InstanceNodeConfigurationDto("Node1");
        {
            InstanceNodeConfiguration nodeConfig = new InstanceNodeConfiguration();
            nodeConfig.applications.add(TestFactory.createAppConfig(product));
            nodeConfig.uuid = instanceConfig.uuid;
            nodeConfig.autoStart = true;
            // wrong name (intentionally). the backend must correct the name.
            nodeConfig.name = "DemoInstance-Node1-Config";
            nodeDto.nodeConfiguration = nodeConfig;
        }
        instanceResource.update("DemoInstance", new InstanceConfigurationDto(null, Collections.singletonList(nodeDto)), null,
                "1");

        // Check node configuration
        InstanceNodeConfigurationListDto instanceConfigDto = instanceResource.getNodeConfigurations("DemoInstance", "2");
        List<InstanceNodeConfigurationDto> availableNodeConfigs = instanceConfigDto.nodeConfigDtos;
        Map<String, InstanceNodeConfigurationDto> node2Config = InstanceNodeConfigurationDto.groupByNode(availableNodeConfigs);
        assertEquals(4, node2Config.size()); // one for each node available

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
        instanceResource.update("DemoInstance", new InstanceConfigurationDto(null, Collections.singletonList(nodeDto)), null,
                "2");

        // Check the updated configuration
        instanceConfigDto = instanceResource.getNodeConfigurations("DemoInstance", "3");
        availableNodeConfigs = instanceConfigDto.nodeConfigDtos;
        node2Config = InstanceNodeConfigurationDto.groupByNode(availableNodeConfigs);

        // Master should not have a configuration
        verifyNodeDto(node2Config.get(Minion.DEFAULT_NAME), false);
    }

    /** Creates a new instance within the given group. A new node config is created for each passed node name */
    private void createInstance(RemoteService remote, Path tmp, String groupName, ProductManifest product, String instanceName,
            String... nodeNames) {

        InstanceConfiguration instanceConfig = TestFactory.createInstanceConfig(instanceName, product);
        try (BHive hive = new BHive(tmp.resolve("hive").toUri(), new ActivityReporter.Null())) {
            PushOperation pushOperation = new PushOperation();
            Builder instanceManifest = new InstanceManifest.Builder().setInstanceConfiguration(instanceConfig);

            for (String nodeName : nodeNames) {
                InstanceNodeConfiguration nodeConfig = new InstanceNodeConfiguration();
                nodeConfig.uuid = UuidHelper.randomId();
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
            assertNull(nodeDto.nodeConfiguration);
        }
    }

    @Test
    void purposes(InstanceGroupResource root) {
        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        assertIterableEquals(Arrays.asList(InstancePurpose.values()), root.getInstanceResource(group.name).getPurposes());
    }

    @Test
    void crud(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.uuid = root.createUuid(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        assertEquals(1, res.list().size());

        InstanceConfiguration read = res.read(instance.uuid);
        assertEquals(product, read.product);
        assertEquals(instance.uuid, read.uuid);
        assertEquals("My Instance", read.name);
        assertEquals(InstancePurpose.PRODUCTIVE, read.purpose);

        read.name = "New Desc";
        res.update(instance.uuid, new InstanceConfigurationDto(read, null), null, "1");

        InstanceConfiguration reread = res.read(instance.uuid);
        assertEquals("New Desc", reread.name);

        res.delete(instance.uuid);
        assertTrue(res.list().isEmpty());
    }

    @Test
    void multipleVersions(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.uuid = root.createUuid(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        instance.name = "My modified Instance";
        res.update(instance.uuid, new InstanceConfigurationDto(instance, null), null, "1");

        InstanceConfiguration read = res.read(instance.uuid);
        assertEquals(read.name, instance.name);

        List<InstanceVersionDto> listVersions = res.listVersions(instance.uuid);
        assertEquals(2, listVersions.size());
        assertEquals("1", listVersions.get(0).key.getTag());
        assertEquals("2", listVersions.get(1).key.getTag());

        InstanceConfiguration readVersion = res.readVersion(instance.uuid, "1");
        assertEquals("My Instance", readVersion.name);

        assertThrows(NotFoundException.class, () -> res.readVersion(instance.uuid, "3"));
    }

    @Test
    void checkPortStates(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws Exception {
        InstanceGroupConfiguration group = new InstanceGroupConfiguration();
        group.name = "demo";
        group.description = "Demo";
        root.create(group);

        Manifest.Key product = TestFactory.pushProduct(group.name, remote, tmp).getKey();

        InstanceResource res = root.getInstanceResource(group.name);
        assertTrue(res.list().isEmpty());

        InstanceConfiguration instance = new InstanceConfiguration();
        instance.product = product;
        instance.uuid = root.createUuid(group.name);
        instance.name = "My Instance";
        instance.purpose = InstancePurpose.PRODUCTIVE;

        res.create(instance, null);

        int port = 0;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            port = ss.getLocalPort();
        }

        // it is free now
        Map<Integer, Boolean> portStates = root.getInstanceResource("demo").getPortStates(instance.uuid, "master",
                Collections.singletonList(port));
        assertEquals(1, portStates.size());
        assertEquals(Boolean.FALSE, portStates.get(port));

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            portStates = root.getInstanceResource("demo").getPortStates(instance.uuid, "master", Collections.singletonList(port));
            assertEquals(1, portStates.size());
            assertEquals(Boolean.TRUE, portStates.get(port));
        }
    }

}

package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;

class InstanceProcessControllerTest {

    @Test
    void testStartStopApps(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", false, "{{SLEEP_TIME}}");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);

        // Create controller with the two applications
        Map<String, String> variables = Collections.singletonMap("SLEEP_TIME", "600");
        InstanceProcessController controller = new InstanceProcessController(group.id);
        controller.createProcessControllers(getDeploymentPathProvider(tmp, group.id), variables::get, null, "1", group, null);
        controller.setActiveTag("1");

        // Start all applications with auto-start flags
        controller.startAll(null);
        InstanceNodeStatusDto status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.isAppRunningOrScheduled("App1"));

        // Start the second application
        controller.start(List.of("App2"), null);
        status = controller.getStatus();
        assertTrue(status.isAppRunningOrScheduled("App2"));

        // Stop one application
        controller.stop(List.of("App1"), null);
        status = controller.getStatus();
        assertTrue(!status.isAppRunningOrScheduled("App1"));

        // Stop all remaining
        controller.stopAll(null);
        status = controller.getStatus();
        assertTrue(!status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(!status.areAppsRunningOrScheduled());
        assertTrue(!status.isAppRunningOrScheduled("App2"));
    }

    @Test
    void testMultiVersionApp(@TempDir Path tmp) throws Exception {
        InstanceProcessController controller = new InstanceProcessController("MyInstance");

        // Create two applications in version 1 and add to the controller
        Path path = tmp.resolve("1");
        ProcessConfiguration app1 = TestFactory.createConfig(path, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(path, "App2", false, "600");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        controller.createProcessControllers(getDeploymentPathProvider(path, group.id), null, null, "1", group, null);

        // Create two applications in version 2 and add to the controller
        path = tmp.resolve("2");
        app1 = TestFactory.createConfig(path, "App1", true, "600");
        app2 = TestFactory.createConfig(path, "App2", false, "600");
        group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        controller.createProcessControllers(getDeploymentPathProvider(path, group.id), null, null, "2", group, null);

        // Activate and start version 1
        controller.setActiveTag("1");
        controller.startAll(null);

        // Application 1 must be running in the expected version
        InstanceNodeStatusDto status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(!status.areAppsRunningOrScheduledInVersion("2"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(!status.isAppRunningOrScheduled("App2"));

        // Upgrade active version
        controller.setActiveTag("2");
        controller.start(List.of("App2"), null);
        status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.areAppsRunningOrScheduledInVersion("2"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(status.isAppRunningOrScheduled("App2"));

        // Try to launch applications again - NOOP in new implementation
        assertDoesNotThrow(() -> controller.start(List.of("App1"), null));
        assertDoesNotThrow(() -> controller.start(List.of("App2"), null));

        // Move back to version 1 and try again
        controller.setActiveTag("1");
        assertDoesNotThrow(() -> controller.start(List.of("App1"), null));
        assertDoesNotThrow(() -> controller.start(List.of("App2"), null));

        // Stop all applications
        controller.stopAll(null);
    }

    @Test
    void testAutoStartAndRecover(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", false, "600");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        group.autoStart = true;

        // Create controller with the two applications
        InstanceProcessController controller = new InstanceProcessController(group.id);
        controller.createProcessControllers(getDeploymentPathProvider(tmp, group.id), null, null, "1", group, null);
        controller.setActiveTag("1");

        // Launch auto-start
        controller.autoStart();
        InstanceNodeStatusDto status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(!status.isAppRunningOrScheduled("App2"));

        // Detach first and create new controller (simulate restart)
        controller.detach();
        controller = new InstanceProcessController(group.id);
        controller.createProcessControllers(getDeploymentPathProvider(tmp, group.id), null, null, "1", group, null);
        controller.setActiveTag("1");

        // Recover running applications
        controller.recover();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(!status.isAppRunningOrScheduled("App2"));

        // Stop all applications
        controller.stopAll(null);
    }

    @Test
    void testStartStopOrder(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", true, "600");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);

        // Create controller with the two applications
        InstanceProcessController controller = new InstanceProcessController(group.id);
        controller.createProcessControllers(getDeploymentPathProvider(tmp, group.id), null, null, "1", group, null);
        controller.setActiveTag("1");

        // create a control group and directly assign it - usually this comes from the configuration.
        ProcessControlGroupConfiguration pcgc = new ProcessControlGroupConfiguration();
        pcgc.name = "Test Group";
        pcgc.processOrder.add("App2");
        pcgc.processOrder.add("App1");

        ProcessList processList = controller.getProcessList("1");
        processList.setControlGroups(Collections.singletonList(pcgc));

        // Add listeners to verify order
        List<String> order = new ArrayList<>();
        ProcessController pc1 = processList.get("App1");
        ProcessController pc2 = processList.get("App2");
        pc1.addStatusListener((s) -> order.add(pc1.getDescriptor().id));
        pc2.addStatusListener((s) -> order.add(pc2.getDescriptor().id));

        StateListener pc1s = StateListener.createFor(pc1);
        StateListener pc2s = StateListener.createFor(pc2);

        pc1s.expect(ProcessState.RUNNING);
        pc2s.expect(ProcessState.RUNNING);

        // Launch both applications and verify order
        controller.startAll(null);
        assertEquals("App2", order.get(0));
        assertEquals("App1", order.get(1));

        // now wait until it is REALLY (async) set to running.
        pc1s.await(Duration.ofSeconds(5));
        pc2s.await(Duration.ofSeconds(5));

        pc1s.expect(ProcessState.RUNNING_STOP_PLANNED, ProcessState.STOPPED);
        pc2s.expect(ProcessState.RUNNING_STOP_PLANNED, ProcessState.STOPPED);

        // Stop both applications and verify order. Must be reversed
        order.clear();
        controller.stopAll(null);

        // await the state changes.
        pc1s.await(Duration.ofSeconds(5));
        pc2s.await(Duration.ofSeconds(5));

        assertEquals(4, order.size()); // 2 times STOP_PLANNED, 2 times STOPPED
        assertEquals("App1", order.get(2));
        assertEquals("App2", order.get(3));
    }

    private static DeploymentPathProvider getDeploymentPathProvider(Path tmp, String instanceId) {
        return new DeploymentPathProvider(tmp.resolve("fakeDeploy"), tmp.resolve("fakeLogData"), instanceId, "1");
    }
}

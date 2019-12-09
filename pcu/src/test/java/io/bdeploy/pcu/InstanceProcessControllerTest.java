package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;

@ExtendWith(TempDirectory.class)
public class InstanceProcessControllerTest {

    @Test
    public void testStartStopApps(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", false, "{{SLEEP_TIME}}");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);

        // Create controller with the two applications
        Map<String, String> variables = Collections.singletonMap("SLEEP_TIME", "600");
        InstanceProcessController controller = new InstanceProcessController(group.uuid);
        controller.createProcessControllers(new DeploymentPathProvider(tmp, group.uuid), variables::get, "1", group);
        controller.setActiveTag("1");

        // Start all applications with auto-start flags
        controller.start();
        InstanceNodeStatusDto status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.isAppRunningOrScheduled("App1"));

        // Start the second application
        controller.start("App2");
        status = controller.getStatus();
        assertTrue(status.isAppRunningOrScheduled("App2"));

        // Stop one application
        controller.stop("App1");
        status = controller.getStatus();
        assertTrue(!status.isAppRunningOrScheduled("App1"));

        // Stop all remaining
        controller.stop();
        status = controller.getStatus();
        assertTrue(!status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(!status.areAppsRunningOrScheduled());
        assertTrue(!status.isAppRunningOrScheduled("App2"));
    }

    @Test
    public void testMultiVersionApp(@TempDir Path tmp) throws Exception {
        InstanceProcessController controller = new InstanceProcessController("MyInstance");

        // Create two applications in version 1 and add to the controller
        Path path = tmp.resolve("1");
        ProcessConfiguration app1 = TestFactory.createConfig(path, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(path, "App2", false, "600");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        controller.createProcessControllers(new DeploymentPathProvider(path, group.uuid), null, "1", group);

        // Create two applications in version 2 and add to the controller
        path = tmp.resolve("2");
        app1 = TestFactory.createConfig(path, "App1", true, "600");
        app2 = TestFactory.createConfig(path, "App2", false, "600");
        group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        controller.createProcessControllers(new DeploymentPathProvider(path, group.uuid), null, "2", group);

        // Activate and start version 1
        controller.setActiveTag("1");
        controller.start();

        // Application 1 must be running in the expected version
        InstanceNodeStatusDto status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(!status.areAppsRunningOrScheduledInVersion("2"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(!status.isAppRunningOrScheduled("App2"));

        // Upgrade active version
        controller.setActiveTag("2");
        controller.start("App2");
        status = controller.getStatus();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.areAppsRunningOrScheduledInVersion("2"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(status.isAppRunningOrScheduled("App2"));

        // Try to launch applications again
        assertThrows(RuntimeException.class, () -> controller.start("App1"));
        assertThrows(RuntimeException.class, () -> controller.start("App2"));

        // Move back to version 1 and try again
        controller.setActiveTag("1");
        assertThrows(RuntimeException.class, () -> controller.start("App1"));
        assertThrows(RuntimeException.class, () -> controller.start("App2"));

        // Stop all applications
        controller.stop();
    }

    @Test
    public void testAutoStartAndRecover(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", false, "600");
        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);
        group.autoStart = true;

        // Create controller with the two applications
        InstanceProcessController controller = new InstanceProcessController(group.uuid);
        controller.createProcessControllers(new DeploymentPathProvider(tmp, group.uuid), null, "1", group);
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
        controller = new InstanceProcessController(group.uuid);
        controller.createProcessControllers(new DeploymentPathProvider(tmp, group.uuid), null, "1", group);
        controller.setActiveTag("1");

        // Recover running applications
        controller.recover();
        assertTrue(status.areAppsRunningOrScheduled());
        assertTrue(status.areAppsRunningOrScheduledInVersion("1"));
        assertTrue(status.isAppRunningOrScheduled("App1"));
        assertTrue(!status.isAppRunningOrScheduled("App2"));

        // Stop all applications
        controller.stop();
    }

}

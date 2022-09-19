package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.StartupProbeDescriptor;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.jersey.TestServer;

class InstanceProcessControllerParallelTest {

    private final AtomicBoolean fakeStart = new AtomicBoolean(false);

    @RegisterExtension
    TestServer server = new TestServer(false, new Object[] { StartupProbe.class, new AbstractBinder() {

        @Override
        protected void configure() {
            bind(fakeStart).named("startupFinished").to(AtomicBoolean.class);
        }
    } });

    @Test
    void testParallelStartStopOrder(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        ProcessConfiguration app2 = TestFactory.createConfig(tmp, "App2", true, "600");

        fakeStart.set(false);

        HttpEndpoint ep = StartupProbe.createEndpoint(server);
        app1.endpoints.http.add(ep);
        app1.processControl.startupProbe = new StartupProbeDescriptor();
        app1.processControl.startupProbe.endpoint = ep.id;

        ProcessGroupConfiguration group = TestFactory.createGroupConfig("MyInstance", app1, app2);

        // Create controller with the two applications
        InstanceProcessController controller = new InstanceProcessController(group.id);
        controller.createProcessControllers(new DeploymentPathProvider(tmp, group.id), null, null, "1", group, null);
        controller.setActiveTag("1");

        // create a control group and directly assign it - usually this comes from the configuration.
        ProcessControlGroupConfiguration pcgc = new ProcessControlGroupConfiguration();
        pcgc.name = "Test Group";
        pcgc.startType = ProcessControlGroupHandlingType.PARALLEL;
        pcgc.stopType = ProcessControlGroupHandlingType.PARALLEL;
        pcgc.processOrder.add("App1");
        pcgc.processOrder.add("App2");

        ProcessList processList = controller.getProcessList("1");
        processList.setControlGroups(Collections.singletonList(pcgc));

        // Add listeners to verify order
        List<String> order = new ArrayList<>();
        ProcessController pc1 = processList.get("App1");
        ProcessController pc2 = processList.get("App2");
        pc1.addStatusListener((s) -> {
            if (s.newState == ProcessState.RUNNING) {
                order.add(pc1.getDescriptor().id);
            }
        });
        pc2.addStatusListener((s) -> {
            if (s.newState == ProcessState.RUNNING) {
                order.add(pc2.getDescriptor().id);

                // once process 2 actually completed startup, we allow app1 startup probe to continue.
                fakeStart.set(true);
            }
        });

        StateListener pc1s = StateListener.createFor(pc1);
        StateListener pc2s = StateListener.createFor(pc2);

        pc1s.expect(ProcessState.RUNNING);
        pc2s.expect(ProcessState.RUNNING);

        // Launch both applications and verify order
        controller.startAll(null);

        // now wait until it is REALLY (async) set to running.
        pc1s.await(Duration.ofSeconds(5));
        pc2s.await(Duration.ofSeconds(5));

        // order is "reversed" since due to parallel startup app2 finished starting before app1 (which has a probe).
        assertEquals("App2", order.get(0));
        assertEquals("App1", order.get(1));

        pc1s.expect(ProcessState.RUNNING_STOP_PLANNED, ProcessState.STOPPED);
        pc2s.expect(ProcessState.RUNNING_STOP_PLANNED, ProcessState.STOPPED);

        // Stop both applications and verify order. Must be reversed
        controller.stopAll(null);

        // await the state changes.
        pc1s.await(Duration.ofSeconds(5));
        pc2s.await(Duration.ofSeconds(5));

        // There is no way to actually verify parallel stop, since we have no means of delaying stop of an application right now.
    }

}

package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessProbeResultDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.StartupProbeDescriptor;
import io.bdeploy.jersey.TestServer;

class ProbeTest {

    private final AtomicBoolean fakeStart = new AtomicBoolean(false);

    @RegisterExtension
    TestServer server = new TestServer(false, new Object[] { StartupProbe.class, new AbstractBinder() {

        @Override
        protected void configure() {
            bind(fakeStart).named("startupFinished").to(AtomicBoolean.class);
        }
    } });

    @Test
    void testProbe(@TempDir Path tmp) throws Exception {
        ProcessConfiguration app1 = TestFactory.createConfig(tmp, "App1", true, "600");
        HttpEndpoint ep = StartupProbe.createEndpoint(server);
        app1.endpoints.http.add(ep);
        app1.processControl.startupProbe = new StartupProbeDescriptor();
        app1.processControl.startupProbe.endpoint = ep.id;

        ProcessController controller = new ProcessController("Test", "V1", app1, tmp.resolve(app1.id));

        StateListener listener = StateListener.createFor(controller).expect(ProcessState.RUNNING_NOT_STARTED);
        fakeStart.set(false);

        controller.start("Test");
        listener.await(Duration.ofSeconds(5));

        // wait until the startup probe was performed at least once and returned "nope"
        while (true) {
            Thread.sleep(100);

            List<ProcessProbeResultDto> lastProbes = controller.getDetails().lastProbes;
            if (!lastProbes.isEmpty() && lastProbes.get(0).status == StartupProbe.STATUS_NOT_STARTED) {
                // a probe was performed and the status is OK -> we continue with the test now
                break;
            }
        }

        assertEquals(ProcessState.RUNNING_NOT_STARTED, controller.getState());

        // update our expectations and let the probe succeed
        listener.expect(ProcessState.RUNNING);
        fakeStart.set(true);

        listener.await(Duration.ofSeconds(5));
        assertEquals(ProcessState.RUNNING, controller.getState());

        listener.expect(ProcessState.STOPPED);
        controller.stop("Test");
    }
}

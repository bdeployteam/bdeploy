package io.bdeploy.jersey.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.jersey.JerseyCachedEventSource;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyEventSubscription;
import io.bdeploy.jersey.JerseySseRegistrar;
import io.bdeploy.jersey.TestServer;

public class SseProxyTest {

    @RegisterExtension
    TestServer producer = new TestServer(SseTestResourceImpl.class, SseActivityProducingResourceImpl.class);

    @RegisterExtension
    TestServer proxy = new TestServer(SseTestResourceImpl.class, new SseProxyResourceImpl());

    @Path("/proxy")
    public interface SseProxyResource {

        @GET
        public void produceProxiedActivity();
    }

    private class SseProxyResourceImpl implements SseProxyResource {

        @Inject
        private ActivityReporter reporter;

        @Override
        public void produceProxiedActivity() {
            try (Activity root = reporter.start("Proxy");
                    NoThrowAutoCloseable closeThis = reporter.proxyActivities(producer.getRemoteService())) {
                SseActivityProducingResource remoteActor = JerseyClientFactory.get(producer.getRemoteService())
                        .getProxyClient(SseActivityProducingResource.class);
                remoteActor.something("scope");
            }
        }

    }

    @Test
    void proxy() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        JerseyClientFactory proxyFactory = JerseyClientFactory.get(proxy.getRemoteService());
        JerseySseRegistrar act = proxyFactory.getEventSource("/activities");
        JerseyEventSubscription sub = act.register((e) -> {
            List<ActivitySnapshot> data = e.readData(ActivitySnapshot.LIST_TYPE);
            // check if the activity of the downstream server is present
            if (data.stream().filter(a -> a.name.equals("Test")).findFirst().isPresent()) {
                latch.countDown();
            }
        });

        // open is async now, and startup of servers is not synchronized - wait for the initial connection.
        // this is required in this test setup only!
        while (!((JerseyCachedEventSource) act).isOpen()) {
            Thread.sleep(10);
        }

        SseProxyResource proxyClient = proxyFactory.getProxyClient(SseProxyResource.class);
        proxyClient.produceProxiedActivity();

        // given the timing, expect at least 2 snapshots to arrive...
        latch.await(4, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());

        sub.close();
    }

}

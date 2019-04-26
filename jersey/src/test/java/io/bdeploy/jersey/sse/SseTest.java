package io.bdeploy.jersey.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.jersey.JerseyCachedEventSource;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyEventSubscription;
import io.bdeploy.jersey.JerseySseRegistrar;
import io.bdeploy.jersey.TestServer;

public class SseTest {

    @RegisterExtension
    TestServer ext = new TestServer(SseTestResourceImpl.class, SseActivityProducingResourceImpl.class);

    private static final Logger log = LoggerFactory.getLogger(SseTest.class);

    @Test
    void simpleEvents(JerseyClientFactory f) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);

        JerseyEventSubscription sub = f.getEventSource("/sse/simple").register((ie) -> {
            log.info("Received Event: " + ie);
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(0, latch.getCount());

        sub.close();
    }

    @Test
    void broadcastEvents(JerseyClientFactory f) throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(10);
        CountDownLatch latch2 = new CountDownLatch(10);

        JerseyEventSubscription eventSource1 = f.getEventSource("/sse/broadcast").register((ie) -> {
            log.info("1: Received Event: " + ie);
            latch1.countDown();
        });

        JerseyEventSubscription eventSource2 = f.getEventSource("/sse/broadcast").register((ie) -> {
            log.info("2: Received Event: " + ie);
            latch2.countDown();
        });

        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        assertTrue(latch2.await(10, TimeUnit.SECONDS));
        assertEquals(0, latch1.getCount());
        assertEquals(0, latch2.getCount());

        eventSource1.close();
        eventSource2.close();
    }

    @Test
    void sseActivitiesTest(JerseyClientFactory f, SseActivityProducingResource rsrc) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        JerseySseRegistrar reg = f.getEventSource("/activities");
        JerseyEventSubscription sub = reg.register(e -> {
            log.info("Received activities: " + e);
            for (ActivitySnapshot snap : e.readData(ActivitySnapshot.LIST_TYPE)) {
                if ("test-scope".equals(snap.scope.get(0))) {
                    latch.countDown();
                }
            }
        });

        rsrc.something("test-scope");

        latch.await(2, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());

        sub.close();

        JerseyCachedEventSource ces = (JerseyCachedEventSource) reg;
        assertFalse(ces.isExpired());

        ces.setReferenceCleanupTimeout(0);
        assertTrue(ces.isExpired());
        assertTrue(ces.isOpen());

        f.cleanUp();
        assertTrue(ces.isExpired());
        assertFalse(ces.isOpen());
    }

    @Test
    void eventSourceCaching(JerseyClientFactory f) throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(10);
        CountDownLatch latch2 = new CountDownLatch(10);

        JerseySseRegistrar reg1 = f.getEventSource("/sse/simple");
        JerseyEventSubscription eventSource1 = reg1.register((ie) -> {
            latch1.countDown();
        });

        JerseySseRegistrar reg2 = f.getEventSource("/sse/simple");
        JerseyEventSubscription eventSource2 = reg2.register((ie) -> {
            latch2.countDown();
        });

        assertEquals(reg1, reg2);

        assertTrue(latch1.await(10, TimeUnit.SECONDS));
        assertEquals(0, latch1.getCount());

        assertTrue(latch2.await(10, TimeUnit.SECONDS));
        assertEquals(0, latch2.getCount());

        eventSource1.close();
        eventSource2.close();
    }

}

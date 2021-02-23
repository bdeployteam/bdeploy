package io.bdeploy.minion.ws;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.activity.JerseyBroadcastingActivityReporter;
import io.bdeploy.jersey.ws.WebSocketTest;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.minion.TestMinion;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

public class WebSocketActivityProxyTest {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTest.class);
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    @RegisterExtension
    TestMinion ext = new TestMinion();

    @BeforeEach
    void registerResource() {
        ext.register(new ProducerImpl());
        ext.register(new ProxyImpl());
    }

    @Test
    void testWebSocket() throws InterruptedException, ExecutionException {
        LongAdder count = new LongAdder();
        try (ObjectChangeClientWebSocket ws = JerseyClientFactory.get(ext.getRemoteService()).getObjectChangeWebSocket(c -> {
            String serialized = c.details.get(JerseyBroadcastingActivityReporter.OCT_ACTIVIES);
            try {
                List<ActivitySnapshot> acts = serializer.readValue(serialized, ActivitySnapshot.LIST_TYPE);
                if (acts.stream().filter(a -> a.name.equals("Test")).findFirst().isPresent()) {
                    count.increment();
                }
            } catch (Exception e) {
                log.error("Exception while reading activities", e);
            }
        })) {
            ws.subscribe(JerseyBroadcastingActivityReporter.OCT_ACTIVIES, ObjectScope.EMPTY);

            JerseyClientFactory.get(ext.getRemoteService()).getProxyClient(Proxy.class).produce();

            log.info("Received " + count.sum() + " events.");

            // due to async & timing, etc. this might not be really exact, but 3 are expected (should be 6 with perfect timing).
            assertTrue(count.sum() >= 3);

            ws.close();
        }
    }

    @Path("/producer")
    public interface Producer {

        @GET
        public String produce();
    }

    @Path("/proxy")
    public interface Proxy {

        @GET
        public String produce();
    }

    public class ProducerImpl implements Producer {

        @Inject
        private ActivityReporter reporter;

        @Override
        public String produce() {
            Activity start = reporter.start("Test", 5);
            for (int i = 0; i < 6; ++i) {
                start.worked(1);
                Threads.sleep(1000);
            }
            return "done";
        }
    }

    public class ProxyImpl implements Proxy {

        @Inject
        private ActivityReporter reporter;

        @Override
        public String produce() {
            try (Activity root = reporter.start("Proxy");
                    NoThrowAutoCloseable closeThis = reporter.proxyActivities(ext.getRemoteService())) {
                return ResourceProvider.getResource(ext.getRemoteService(), Producer.class, null).produce();
            }
        }

    }

}

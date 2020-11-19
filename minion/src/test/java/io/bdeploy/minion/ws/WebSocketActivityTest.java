package io.bdeploy.minion.ws;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ws.WebSocket;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.Threads;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.ws.WebSocketTest;
import io.bdeploy.minion.TestMinion;

public class WebSocketActivityTest {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTest.class);

    @RegisterExtension
    TestMinion ext = new TestMinion();

    @BeforeEach
    void registerResource() {
        ext.register(new ProducerImpl());
    }

    @Test
    void testWebSocket(RemoteService service, Producer producer) throws InterruptedException, ExecutionException {
        LongAdder count = new LongAdder();
        try (AsyncHttpClient client = JerseyClientFactory.get(service).getWebSocketClient()) {
            WebSocket ws = JerseyClientFactory.get(service)
                    .getAuthenticatedWebSocket(client, Collections.emptyList(), "/activities", m -> {
                        List<ActivitySnapshot> acts = StorageHelper.fromRawBytes(m, ActivitySnapshot.LIST_TYPE);

                        if (acts.get(0).name.equals("Test")) {
                            count.increment();
                        }
                    }, t -> {
                        log.error("Error", t);
                    }, s -> {
                        log.info("Close!");
                    }).get();

            producer.produce();

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

}

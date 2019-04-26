package io.bdeploy.jersey.sse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

@Path("/sse")
public class SseTestResourceImpl {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Sse sse;
    private final SseBroadcaster bc;
    private ScheduledFuture<?> current;

    public SseTestResourceImpl(@Context Sse sse) {
        this.sse = sse;
        this.bc = sse.newBroadcaster();
    }

    @GET
    @Path("/simple")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink sink) {
        CountDownLatch latch = new CountDownLatch(10);

        scheduler.scheduleAtFixedRate(() -> {
            latch.countDown();

            OutboundSseEvent event = sse.newEventBuilder().name("latch").data(Long.class, latch.getCount()).build();
            sink.send(event);

            if (latch.getCount() == 0) {
                throw new RuntimeException("cancel this");
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    @GET
    @Path("/broadcast")
    public void subscribeBroadcast(@Context SseEventSink sink) {
        bc.register(sink);

        // the first registration starts the broadcasting.
        if (current == null) {
            CountDownLatch latch = new CountDownLatch(10);

            // this assumes that all test clients will be present within 200 milliseconds
            current = scheduler.scheduleAtFixedRate(() -> {
                latch.countDown();

                OutboundSseEvent event = sse.newEventBuilder().name("bc-latch").data(Long.class, latch.getCount()).build();
                sink.send(event);

                if (latch.getCount() == 0) {
                    throw new RuntimeException("cancel this");
                }
            }, 200, 100, TimeUnit.MILLISECONDS);
        }
    }

}

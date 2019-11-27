package io.bdeploy.ui.api.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.jersey.JerseyServer;

@Path("/attach-events")
@Singleton
public class ManagedServersAttachEventResourceImpl {

    private static final long KEEP_ALIVE_INTERVAL = 30; // seconds

    private static final Logger log = LoggerFactory.getLogger(ManagedServersAttachEventResourceImpl.class);

    private final Sse sse;
    private final SseBroadcaster bc;

    @Inject
    public ManagedServersAttachEventResourceImpl(@Context Sse sse,
            @Named(JerseyServer.BROADCAST_EXECUTOR) ScheduledExecutorService executor) {
        this.sse = sse;
        this.bc = sse.newBroadcaster();

        executor.scheduleAtFixedRate(this::keepAlive, KEEP_ALIVE_INTERVAL, KEEP_ALIVE_INTERVAL, TimeUnit.SECONDS);
    }

    private void keepAlive() {
        try {
            OutboundSseEvent event = sse.newEventBuilder().comment("keep-alive").build();
            bc.broadcast(event);
        } catch (Exception e) {
            log.error("Cannot broadcast server update", e);
        }
    }

    @POST
    public void setLocalAttached(String groupName) {
        try {
            OutboundSseEvent event = sse.newEventBuilder().name("attach").data(groupName).mediaType(MediaType.TEXT_PLAIN_TYPE)
                    .build();
            bc.broadcast(event);
        } catch (Exception e) {
            log.error("Cannot broadcast server update", e);
        }
    }

    @GET
    public void registerListener(@Context SseEventSink sink) {
        bc.register(sink);
    }

}

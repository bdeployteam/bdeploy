package io.bdeploy.ui.api.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.ui.dto.InstanceUpdateEventDto;
import io.bdeploy.ui.dto.InstanceUpdateEventDto.InstanceUpdateEventType;

@Singleton
@Path("/instance-updates")
public class InstanceEventBroadcaster {

    private static final long KEEP_ALIVE_INTERVAL = 30; // seconds

    private static final Logger log = LoggerFactory.getLogger(InstanceEventBroadcaster.class);

    private final Sse sse;
    private final SseBroadcaster bc;

    @Inject
    public InstanceEventBroadcaster(@Context Sse sse, @Named(JerseyServer.BROADCAST_EXECUTOR) ScheduledExecutorService executor) {
        this.sse = sse;
        this.bc = sse.newBroadcaster();

        executor.scheduleAtFixedRate(this::keepAlive, KEEP_ALIVE_INTERVAL, KEEP_ALIVE_INTERVAL, TimeUnit.SECONDS);

        UiResources.getInstanceEventManager().register(this);
    }

    @GET
    public void register(@Context SseEventSink sink) {
        bc.register(sink);
    }

    public void keepAlive() {
        try {
            OutboundSseEvent event = sse.newEventBuilder().comment("keep-alive").build();
            bc.broadcast(event);
        } catch (Exception e) {
            log.error("Cannot broadcast instance update", e);
        }
    }

    public void create(String instanceId, Manifest.Key newInstanceKey) {
        send(instanceId, new InstanceUpdateEventDto(newInstanceKey, InstanceUpdateEventType.CREATE));
    }

    public void stateChanged(String instanceId, Manifest.Key instanceKey) {
        send(instanceId, new InstanceUpdateEventDto(instanceKey, InstanceUpdateEventType.STATE_CHANGE));
    }

    private void send(String instanceId, InstanceUpdateEventDto dto) {
        try {
            OutboundSseEvent event = sse.newEventBuilder().name(instanceId).data(dto).mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .build();
            bc.broadcast(event);
        } catch (Exception e) {
            log.error("Cannot broadcast instance update", e);
        }
    }

}

package io.bdeploy.jersey;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivitySnapshot;

/**
 * Resource producing SSEs (Server Sent Events) for all activities on the server.
 */
@Singleton
@Path("/activities")
public class JerseySseActivityResourceImpl {

    private static final Logger log = LoggerFactory.getLogger(JerseySseActivityResourceImpl.class);

    @Inject
    private JerseySseActivityReporter reporter;

    private final Sse sse;
    private final SseBroadcaster bc;
    private boolean lastBroadcastWasEmpty = true;
    private long lastBroadcastTime = 0;

    @Inject
    public JerseySseActivityResourceImpl(@Context Sse sse,
            @Named(JerseyServer.BROADCAST_EXECUTOR) ScheduledExecutorService executor) {
        this.sse = sse;
        this.bc = sse.newBroadcaster();

        executor.scheduleAtFixedRate(this::broadcast, 0, 2, TimeUnit.SECONDS);
    }

    @GET
    public void register(@Context SseEventSink sink) {
        bc.register(sink);
    }

    @DELETE
    @Path("/{taskId}")
    public void cancelTask(@PathParam("taskId") String taskId) {
        JerseySseActivity activity = reporter.getGlobalActivities().stream().filter(Objects::nonNull)
                .filter(a -> a.getUuid().equals(taskId)).findFirst().orElse(null);
        if (activity != null) {
            activity.requestCancel();
        }
    }

    private void broadcast() {
        try {
            List<ActivitySnapshot> list = reporter.getGlobalActivities().stream().filter(Objects::nonNull)
                    .map(JerseySseActivity::snapshot).collect(Collectors.toList());
            long now = System.currentTimeMillis();
            if (list.isEmpty() && lastBroadcastWasEmpty && (now - lastBroadcastTime) < TimeUnit.SECONDS.toMillis(30)) {
                return; // don't broadcast empty lists more than once every 30 seconds.
            }
            lastBroadcastWasEmpty = list.isEmpty();
            lastBroadcastTime = now;

            // TODO: currently all activities are broadcasted to all receivers and the client filters.
            // This allows for more complex filter logic on the client, but on the other hand produces potentially
            // a lot of traffic to all connected web-apps. If this ever becomes a problem, we need to rethink this.
            OutboundSseEvent event = sse.newEventBuilder().name("activities").data(ActivitySnapshot.LIST_TYPE, list)
                    .mediaType(MediaType.APPLICATION_JSON_TYPE).build();
            bc.broadcast(event);
        } catch (Exception e) {
            log.error("Cannot broadcast activities", e);
        }
    }

}

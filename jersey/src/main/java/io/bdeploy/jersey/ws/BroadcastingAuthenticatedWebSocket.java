package io.bdeploy.jersey.ws;

import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.ws.rs.core.Response.Status;

import org.glassfish.grizzly.websockets.Broadcaster;
import org.glassfish.grizzly.websockets.OptimizedBroadcaster;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketAdapter;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.jersey.JerseyAuthenticationProvider;

public class BroadcastingAuthenticatedWebSocket extends WebSocketApplication implements JerseyEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BroadcastingAuthenticatedWebSocket.class);

    private final Broadcaster broadcaster;

    private final Function<Object, byte[]> serializer;

    private final KeyStore authStore;

    private final ScheduledExecutorService autoCloser = Executors.newSingleThreadScheduledExecutor();

    public BroadcastingAuthenticatedWebSocket(Function<Object, byte[]> serializer, KeyStore authStore) {
        this.serializer = serializer;
        this.authStore = authStore;
        this.broadcaster = new OptimizedBroadcaster();
    }

    @Override
    public void send(Object message) {
        this.broadcaster.broadcast(getWebSockets(), serializer.apply(message));
    }

    @Override
    public void onConnect(WebSocket socket) {
        ScheduledFuture<?> schedule = autoCloser
                .schedule(() -> socket.close(Status.UNAUTHORIZED.getStatusCode(), "No Token received"), 5, TimeUnit.SECONDS);
        socket.add(new WebSocketAdapter() {

            @Override
            public void onMessage(WebSocket s, String text) {
                ApiAccessToken token = null;
                try {
                    token = JerseyAuthenticationProvider.validateToken(text, authStore);
                } catch (Exception e) {
                    log.error("Cannot parse authentication token: ", e);
                }

                schedule.cancel(false);

                if (token == null) {
                    log.warn("Invalid authentication from client, closing");
                    s.close(Status.UNAUTHORIZED.getStatusCode(), "Invalid Authentication Token");
                } else {
                    BroadcastingAuthenticatedWebSocket.super.onConnect(socket);
                }
            }
        });
    }

}

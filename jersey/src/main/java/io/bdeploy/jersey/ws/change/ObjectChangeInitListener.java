package io.bdeploy.jersey.ws.change;

import java.io.IOException;
import java.security.KeyStore;
import java.util.concurrent.ScheduledFuture;

import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketAdapter;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.jersey.JerseyAuthenticationProvider;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeInitDto;
import jakarta.ws.rs.core.Response.Status;

/**
 * A {@link WebSocketListener} which listens for a {@link ObjectChangeInitDto} message.
 * <p>
 * Once a valid message has been received, the listener removes itself from the socket, cancels the automatic closing of the
 * {@link WebSocket} and registered the {@link WebSocket} with its {@link ObjectChangeWebSocket} manager.
 */
final class ObjectChangeInitListener extends WebSocketAdapter {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeInitListener.class);

    private final ObjectChangeWebSocket manager;
    private final KeyStore authStore;
    private final WebSocket socket;
    private final ScheduledFuture<?> kicker;
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    ObjectChangeInitListener(ObjectChangeWebSocket manager, KeyStore authStore, WebSocket socket, ScheduledFuture<?> kicker) {
        this.manager = manager;
        this.authStore = authStore;
        this.socket = socket;
        this.kicker = kicker;
    }

    @Override
    public void onMessage(WebSocket s, String text) {
        ObjectChangeInitDto init;
        try {
            init = serializer.readValue(text, ObjectChangeInitDto.class);
        } catch (IOException e) {
            log.error("Cannot read init DTO", e);
            s.close(Status.UNAUTHORIZED.getStatusCode(), "Invalid Init Message");
            return;
        }

        ApiAccessToken token = null;
        try {
            token = JerseyAuthenticationProvider.validateToken(init.token, authStore);
        } catch (Exception e) {
            log.error("Cannot parse authentication token: {}", e.toString());
        }

        kicker.cancel(false);

        if (token == null) {
            log.warn("Invalid authentication from client, closing");
            s.close(Status.UNAUTHORIZED.getStatusCode(), "Invalid Authentication Token");
        } else {
            socket.remove(this); // make sure we're not called on every message received.
            manager.add(socket); // and now register with the manager.
        }
    }
}
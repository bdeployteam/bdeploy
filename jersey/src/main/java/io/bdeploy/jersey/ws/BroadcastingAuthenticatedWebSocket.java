package io.bdeploy.jersey.ws;

import java.io.IOException;
import java.security.KeyStore;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response.Status;

import org.glassfish.grizzly.websockets.Broadcaster;
import org.glassfish.grizzly.websockets.OptimizedBroadcaster;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketAdapter;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.jersey.JerseyAuthenticationProvider;

public class BroadcastingAuthenticatedWebSocket extends WebSocketApplication implements JerseyEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BroadcastingAuthenticatedWebSocket.class);

    private final Broadcaster broadcaster;

    private final Function<Object, byte[]> serializer;

    private final KeyStore authStore;

    private final ScheduledExecutorService autoCloser = Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentMap<WebSocket, List<String>> webSockets = new ConcurrentHashMap<>();

    public BroadcastingAuthenticatedWebSocket(Function<Object, byte[]> serializer, KeyStore authStore) {
        this.serializer = serializer;
        this.authStore = authStore;
        this.broadcaster = new OptimizedBroadcaster();
    }

    @Override
    public void send(Object message, List<String> scope) {
        this.broadcaster.broadcast(getWebSockets(scope), serializer.apply(message));
    }

    @Override
    public void onConnect(WebSocket socket) {
        ScheduledFuture<?> schedule = autoCloser
                .schedule(() -> socket.close(Status.UNAUTHORIZED.getStatusCode(), "No Token received"), 5, TimeUnit.SECONDS);
        socket.add(new WebSocketAdapter() {

            @Override
            public void onMessage(WebSocket s, String text) {
                WebSocketInitDto init;
                try {
                    init = JacksonHelper.createObjectMapper(MapperType.JSON).readValue(text, WebSocketInitDto.class);
                } catch (IOException e) {
                    log.error("Cannot read WebSocket init DTO", e);
                    s.close(Status.UNAUTHORIZED.getStatusCode(), "Invalid Init Message");
                    return;
                }

                ApiAccessToken token = null;
                try {
                    token = JerseyAuthenticationProvider.validateToken(init.token, authStore);
                } catch (Exception e) {
                    log.error("Cannot parse authentication token: ", e);
                }

                schedule.cancel(false);

                if (token == null) {
                    log.warn("Invalid authentication from client, closing");
                    s.close(Status.UNAUTHORIZED.getStatusCode(), "Invalid Authentication Token");
                } else {
                    socket.remove(this); // make sure we're not called on every message received.
                    add(socket, init.scope);
                }
            }
        });
    }

    private boolean add(WebSocket socket, List<String> scopes) {
        return webSockets.put(socket, scopes) == null;
    }

    @Override
    public boolean remove(WebSocket socket) {
        return webSockets.remove(socket) != null;
    }

    private Set<WebSocket> getWebSockets(List<String> scope) {
        return webSockets.entrySet().stream().filter(e -> {
            // ignore socket if it's scope is more precise than the message's scope
            if (e.getValue().size() > scope.size()) {
                return false;
            }

            // compare all scope parts. all scope parts on the websocket must be present on the message scope.
            // the message scope is guaranteed to AT LEAST have the websocket scope's size.
            for (int i = 0; i < e.getValue().size(); ++i) {
                if (!e.getValue().get(i).equals(scope.get(i))) {
                    return false;
                }
            }

            return true;
        }).map(Entry::getKey).collect(Collectors.toSet());
    }

}

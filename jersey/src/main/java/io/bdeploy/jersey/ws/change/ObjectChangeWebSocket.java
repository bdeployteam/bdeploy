package io.bdeploy.jersey.ws.change;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.glassfish.grizzly.websockets.Broadcaster;
import org.glassfish.grizzly.websockets.OptimizedBroadcaster;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import jakarta.ws.rs.core.Response.Status;

public class ObjectChangeWebSocket extends WebSocketApplication implements ObjectChangeBroadcaster {

    public static final String OCWS_PATH = "/object-changes";

    static final Logger log = LoggerFactory.getLogger(ObjectChangeWebSocket.class);

    /** Used to send messages to all {@link WebSocket}s. */
    private final Broadcaster broadcaster;

    /** Used to verify tokens during authentication process. */
    private final KeyStore authStore;

    /** Keeps track of {@link WebSocket}s in authenticating state and closes them if they fail to authenticate in time. */
    private final ScheduledExecutorService autoCloser = Executors.newSingleThreadScheduledExecutor();

    /** Keeps track of registrations per {@link WebSocket} */
    private final ConcurrentMap<WebSocket, ObjectChangeRegistration> webSockets = new ConcurrentHashMap<>();

    /** Listeners hooked to each {@link ObjectChangeRegistration} as it is created, mainly for testing */
    private final List<Consumer<ObjectChangeRegistration>> listeners = new ArrayList<>();

    /** A JSON mapper which is used to serialize and de-serialize communication DTOs */
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    public ObjectChangeWebSocket(KeyStore authStore) {
        this.authStore = authStore;
        this.broadcaster = new OptimizedBroadcaster();
    }

    @Override
    public void send(ObjectChangeDto change) {
        try {
            Set<WebSocket> targets = getWebSockets(change);
            this.broadcaster.broadcast(targets, serializer.writeValueAsString(change));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON to WebSocket", e);
        }
    }

    @Override
    public void sendBestMatching(List<ObjectChangeDto> changes) {
        try {
            Map<ObjectChangeDto, List<WebSocket>> targets = new HashMap<>();
            for (Map.Entry<WebSocket, ObjectChangeRegistration> entry : webSockets.entrySet()) {
                ObjectChangeDto best = null;
                int bestScore = 0;

                for (ObjectChangeDto change : changes) {
                    // for each websocket, find the change DTO which has the highest score.
                    ObjectScope match = entry.getValue().getBestScoring(change.type, change.scope);
                    if (match != null) {
                        int score = match.score(change.scope);

                        if (score > bestScore || (score == bestScore && best.scope.length() > match.length())) {
                            // this websocket has a match, choose the best score.
                            bestScore = score;
                            best = change;
                        }
                    }
                }

                // if we found a match, record it.
                if (best != null) {
                    targets.computeIfAbsent(best, (k) -> new ArrayList<>()).add(entry.getKey());
                }
            }

            for (Map.Entry<ObjectChangeDto, List<WebSocket>> target : targets.entrySet()) {
                this.broadcaster.broadcast(target.getValue(), serializer.writeValueAsString(target.getKey()));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON to WebSocket", e);
        }
    }

    @Override
    public void onConnect(WebSocket socket) {
        // Register to be kicked automatically after failing to authorize after a few seconds.
        ScheduledFuture<?> schedule = autoCloser
                .schedule(() -> socket.close(Status.UNAUTHORIZED.getStatusCode(), "No Token received"), 5, TimeUnit.SECONDS);

        // Start listening to the initialization message
        socket.add(new ObjectChangeInitListener(this, authStore, socket, schedule));
    }

    @Override
    protected boolean add(WebSocket socket) {
        // start listening to registrations and registration changes.
        ObjectChangeRegistration reg = new ObjectChangeRegistration();
        listeners.forEach(reg::addListener);
        socket.add(new ObjectChangeRegistrationListener(reg));

        return webSockets.put(socket, reg) == null;
    }

    @Override
    public boolean remove(WebSocket socket) {
        return webSockets.remove(socket) != null;
    }

    @Override
    protected Set<WebSocket> getWebSockets() {
        throw new UnsupportedOperationException();
    }

    /** Get all websockets which are interested in the given change */
    private Set<WebSocket> getWebSockets(ObjectChangeDto change) {
        return webSockets.entrySet().stream().filter(e -> e.getValue().matches(change.type, change.scope)).map(Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void addListener(Consumer<ObjectChangeRegistration> listener) {
        listeners.add(listener);
        for (ObjectChangeRegistration existing : webSockets.values()) {
            existing.addListener(listener);
        }
    }

}

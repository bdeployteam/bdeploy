package io.bdeploy.jersey.ws;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketTextListener;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;

/**
 * Client side {@link WebSocketTextListener} adapter which listens delegates messages and errors.
 */
public class WebSocketAuthenticatingMessageListener extends DefaultWebSocketListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthenticatingMessageListener.class);

    private final Consumer<byte[]> onMessage;
    private final Consumer<Throwable> onError;
    private final String token;
    private final Consumer<WebSocket> onClose;
    private final List<String> scope;

    public WebSocketAuthenticatingMessageListener(String token, List<String> scope, Consumer<byte[]> onMessage,
            Consumer<Throwable> onError, Consumer<WebSocket> onClose) {
        this.token = token;
        this.scope = scope;
        this.onMessage = onMessage;
        this.onError = onError;
        this.onClose = onClose;
    }

    @Override
    public void onOpen(WebSocket websocket) {
        WebSocketInitDto init = new WebSocketInitDto();

        init.token = token;
        init.scope = scope;

        try {
            websocket.sendMessage(JacksonHelper.createObjectMapper(MapperType.JSON).writeValueAsString(init));
        } catch (JsonProcessingException e) {
            log.error("Cannot send WebSocket initialization message", e);
            return;
        }
        super.onOpen(websocket);
    }

    @Override
    public void onClose(WebSocket websocket) {
        onClose.accept(websocket);
        super.onClose(websocket);
    }

    @Override
    public void onError(Throwable t) {
        onError.accept(t);
    }

    @Override
    public void onMessage(String message) {
        onMessage.accept(message.getBytes());
    }

    @Override
    public void onMessage(byte[] message) {
        onMessage.accept(message);
    }

}

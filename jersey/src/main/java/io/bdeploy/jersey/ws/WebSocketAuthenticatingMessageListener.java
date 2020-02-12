package io.bdeploy.jersey.ws;

import java.util.function.Consumer;

import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketTextListener;

/**
 * Client side {@link WebSocketTextListener} adapter which listens delegates messages and errors.
 */
public class WebSocketAuthenticatingMessageListener extends DefaultWebSocketListener {

    private final Consumer<byte[]> onMessage;
    private final Consumer<Throwable> onError;
    private final String token;
    private final Consumer<WebSocket> onClose;

    public WebSocketAuthenticatingMessageListener(String token, Consumer<byte[]> onMessage, Consumer<Throwable> onError,
            Consumer<WebSocket> onClose) {
        this.token = token;
        this.onMessage = onMessage;
        this.onError = onError;
        this.onClose = onClose;
    }

    @Override
    public void onOpen(WebSocket websocket) {
        websocket.sendMessage(token);
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

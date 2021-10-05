package io.bdeploy.jersey.ws.change.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocket;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeInitDto;

/**
 * {@link WebSocket} listener which handles communication with the {@link ObjectChangeWebSocket} on the server.
 */
public class ObjectChangeClientListener extends DefaultWebSocketListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeClientListener.class);
    private final String token;
    private final Consumer<ObjectChangeDto> onChanges;
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    public ObjectChangeClientListener(String token, Consumer<ObjectChangeDto> onChanges) {
        this.token = token;
        this.onChanges = onChanges;
    }

    @Override
    public void onOpen(WebSocket websocket) {
        ObjectChangeInitDto init = new ObjectChangeInitDto();
        init.token = token;

        try {
            websocket.sendMessage(JacksonHelper.createObjectMapper(MapperType.JSON).writeValueAsString(init));
        } catch (JsonProcessingException e) {
            log.error("Cannot send WebSocket initialization message", e);
            return;
        }

        super.onOpen(websocket);
    }

    @Override
    public void onError(Throwable t) {
        log.error("Error on object change web socket client", t);
    }

    private void processMessage(byte[] message) {
        try {
            onChanges.accept(serializer.readValue(message, ObjectChangeDto.class));
        } catch (IOException e) {
            log.error("Cannot process object change message", e);
        }
    }

    @Override
    public void onMessage(String message) {
        processMessage(message.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onMessage(byte[] message) {
        processMessage(message);
    }

}

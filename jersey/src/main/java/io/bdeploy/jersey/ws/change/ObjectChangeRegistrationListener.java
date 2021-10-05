package io.bdeploy.jersey.ws.change;

import java.io.IOException;

import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeRegistrationDto;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeRegistrationDto.RegistrationAction;

/**
 * Listens to messages on a {@link WebSocket} and adjusts registrations according to instructions received.
 */
public class ObjectChangeRegistrationListener extends WebSocketAdapter {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeRegistrationListener.class);

    private final ObjectChangeRegistration registration;
    private final ObjectMapper serializer = JacksonHelper.createObjectMapper(MapperType.JSON);

    public ObjectChangeRegistrationListener(ObjectChangeRegistration registration) {
        this.registration = registration;
    }

    @Override
    public void onMessage(WebSocket socket, String text) {
        try {
            ObjectChangeRegistrationDto change = serializer.readValue(text, ObjectChangeRegistrationDto.class);
            if (change.action == RegistrationAction.ADD) {
                registration.add(change.type, change.scope);
            } else if (change.action == RegistrationAction.REMOVE) {
                registration.remove(change.type, change.scope);
            } else {
                log.error("Unknown action to perform on registration: {}", change.action);
            }
        } catch (IOException e) {
            log.error("Cannot read registration from WebSocket", e);
        }
    }

}

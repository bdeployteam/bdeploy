package io.bdeploy.jersey.ws.change.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ws.WebSocket;

import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeRegistrationDto;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeRegistrationDto.RegistrationAction;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;

/**
 * Wraps a {@link WebSocket} with functionality regarding {@link ObjectChangeRegistrationDto}.
 */
public class ObjectChangeClientWebSocket implements NoThrowAutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ObjectChangeClientWebSocket.class);

    private final AsyncHttpClient client;
    private final WebSocket connection;

    public ObjectChangeClientWebSocket(AsyncHttpClient client, WebSocket webSocket) {
        this.client = client;
        this.connection = webSocket;
    }

    /**
     * Subscribe to subsequent changes of the given type and scope.
     */
    public void subscribe(String type, ObjectScope scope) {
        writeChange(RegistrationAction.ADD, type, scope);
    }

    /**
     * Unsubscribe from subsequent changes of the given type and scope.
     */
    public void unsubscribe(String type, ObjectScope scope) {
        writeChange(RegistrationAction.REMOVE, type, scope);
    }

    @Override
    public void close() {
        connection.close();
        client.close();
    }

    private void writeChange(RegistrationAction action, String type, ObjectScope scope) {
        try {
            ObjectChangeRegistrationDto reg = new ObjectChangeRegistrationDto();
            reg.action = action;
            reg.type = type;
            reg.scope = scope;

            connection.sendMessage(JacksonHelper.getDefaultJsonObjectMapper().writeValueAsString(reg));
        } catch (IOException e) {
            log.error("Cannot subscribe to object changes", e);
        }
    }

}

package io.bdeploy.jersey.ws.change.msg;

import org.glassfish.grizzly.websockets.WebSocket;

/**
 * A message to instruct a change in a {@link WebSocket} event registration on the server.
 */
public class ObjectChangeRegistrationDto {

    /**
     * Whether to add or remove a registration
     */
    public enum RegistrationAction {
        ADD,
        REMOVE
    }

    /** The action to perform on the server */
    public RegistrationAction action;

    /** The type of the object to apply changes to */
    public String type;

    /** The scope of the object to add or remove a registration for */
    public ObjectScope scope = ObjectScope.EMPTY;

}

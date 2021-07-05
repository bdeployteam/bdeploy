package io.bdeploy.jersey.ws.change;

import java.util.List;

import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;

/**
 * A destination sink which broadcasts object changes.
 */
public interface ObjectChangeBroadcaster {

    /**
     * @param change the changes to broadcast to any interested (remote) party.
     */
    public void send(ObjectChangeDto change);

    /**
     * @param changes the changes to broadcast, sending only the best matching scoped change to each websocket.
     */
    public void sendBestMatching(List<ObjectChangeDto> changes);

}

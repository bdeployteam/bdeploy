package io.bdeploy.jersey.ws.change;

import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;

/**
 * A destination sink which broadcasts object changes.
 */
public interface ObjectChangeBroadcaster {

    /**
     * @param change the changes to broadcast to any interested (remote) party.
     */
    public void send(ObjectChangeDto change);

}

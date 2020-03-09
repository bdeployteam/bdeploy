package io.bdeploy.jersey.ws;

import java.util.List;

public interface JerseyEventBroadcaster {

    public void send(Object message, List<String> scope);

}

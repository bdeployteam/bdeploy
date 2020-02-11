package io.bdeploy.jersey;

public interface JerseyEventBroadcaster {

    public void send(Object message);

}

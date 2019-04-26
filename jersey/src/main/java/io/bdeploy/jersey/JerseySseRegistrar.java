package io.bdeploy.jersey;

import java.util.function.Consumer;

import javax.ws.rs.sse.InboundSseEvent;

/**
 * An authority managing SSE subscriptions for a certain event source.
 */
public interface JerseySseRegistrar {

    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent);

    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError);

    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError, Runnable onComplete);

}

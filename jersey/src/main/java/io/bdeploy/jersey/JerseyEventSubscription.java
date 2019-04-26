package io.bdeploy.jersey;

import io.bdeploy.common.NoThrowAutoCloseable;

public class JerseyEventSubscription implements NoThrowAutoCloseable {

    private final JerseyCachedEventSource source;
    private final Object onEvent;
    private final Object onError;
    private final Object onComplete;

    JerseyEventSubscription(JerseyCachedEventSource source, Object onEvent, Object onError, Object onComplete) {
        this.source = source;
        this.onEvent = onEvent;
        this.onError = onError;
        this.onComplete = onComplete;

        source.open();
    }

    @Override
    public void close() {
        if (onEvent != null) {
            this.source.unregisterOnEvent(onEvent);
        }

        if (onError != null) {
            this.source.unregisterOnError(onError);
        }

        if (onComplete != null) {
            this.source.unregisterOnComplete(onComplete);
        }

        this.source.close();
    }

}

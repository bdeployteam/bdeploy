package io.bdeploy.jersey;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SseEventSource} implementation which automatically {@link #open()}s on {@link #register(Consumer)} calls and allows
 * multiple {@link #open()} and {@link #close()} calls.
 * <p>
 * Additionally the {@link #open()} call is performed on a separate {@link Thread}, so {@link #open()} no longer blocks until a
 * connection has been established.
 */
public class JerseyCachedEventSource implements JerseySseRegistrar {

    private static final long DEFAULT_REFLESS_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
    private static final Logger log = LoggerFactory.getLogger(JerseyCachedEventSource.class);

    private final SseEventSource delegate;
    private final WebTarget target;
    private final AtomicBoolean startup = new AtomicBoolean(false);
    private final AtomicLong refs = new AtomicLong(0l);
    private long refLessSince = 0;
    private long refLessTimeout = DEFAULT_REFLESS_TIMEOUT;

    private final Set<Consumer<InboundSseEvent>> onEventHandlers = new HashSet<>();
    private final Set<Consumer<Throwable>> onErrorHandlers = new HashSet<>();
    private final Set<Runnable> onCompleteHandlers = new HashSet<>();

    public JerseyCachedEventSource(SseEventSource delegate, WebTarget target) {
        this.delegate = delegate;
        this.target = target;
        this.delegate.register(this::onEvent, this::onError, this::onComplete);
    }

    @Override
    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent) {
        return register(onEvent, null, null);
    }

    @Override
    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError) {
        return register(onEvent, onError, null);
    }

    @Override
    public JerseyEventSubscription register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError, Runnable onComplete) {
        JerseyEventSubscription sub = new JerseyEventSubscription(this, onEvent, onError, onComplete);

        if (onEvent != null) {
            this.onEventHandlers.add(onEvent);
        }

        if (onError != null) {
            this.onErrorHandlers.add(onError);
        }

        if (onComplete != null) {
            this.onCompleteHandlers.add(onComplete);
        }

        return sub;
    }

    private void onEvent(InboundSseEvent event) {
        onEventHandlers.forEach(h -> unthrow(() -> h.accept(event)));
    }

    private void onError(Throwable error) {
        onErrorHandlers.forEach(h -> unthrow(() -> h.accept(error)));
    }

    private void onComplete() {
        onCompleteHandlers.forEach(h -> unthrow(h::run));
    }

    private void unthrow(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Cannot call handler", e);
        }
    }

    void open() {
        refs.incrementAndGet();

        if (delegate.isOpen()) {
            return;
        }

        // run in separate thread. caching of this will prevent multiple of those threads to run.
        if (!startup.compareAndSet(false, true)) {
            return; // open in progress elsewhere.
        }
        new Thread(() -> {
            delegate.open();

            // startup finished
            startup.set(false);
        }, "SSE Setup: " + target.getUri()).start();

        refLessSince = 0;
    }

    public boolean isOpen() {
        return !startup.get() && delegate.isOpen();
    }

    void close() {
        long refCount = refs.decrementAndGet();

        if (refCount <= 0) {
            refLessSince = System.currentTimeMillis();
        }
    }

    public boolean isExpired() {
        if (refLessSince == 0 || refs.get() > 0) {
            return false;
        }

        return (System.currentTimeMillis() - refLessSince) >= refLessTimeout;
    }

    void doExpire() {
        onEventHandlers.clear();
        onErrorHandlers.clear();
        onCompleteHandlers.forEach(a -> unthrow(a::run));
        delegate.close();
    }

    void unregisterOnEvent(Object onEvent) {
        this.onEventHandlers.remove(onEvent);
    }

    void unregisterOnError(Object onError) {
        this.onErrorHandlers.remove(onError);
    }

    void unregisterOnComplete(Object onComplete) {
        this.onCompleteHandlers.remove(onComplete);
    }

    public void setReferenceCleanupTimeout(long timeoutMillis) {
        refLessTimeout = timeoutMillis;
    }

}

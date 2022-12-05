package io.bdeploy.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryableScope {

    private static final Logger log = LoggerFactory.getLogger(RetryableScope.class);

    private final AtomicLong iterationCountMax = new AtomicLong(10);
    private final AtomicLong iterationTimeoutMs = new AtomicLong(100);

    private Consumer<Exception> onException = (e) -> {
        log.debug("Retriable scope received exception: ", e);

        try {
            Thread.sleep(iterationTimeoutMs.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("While handling retry: ", ie);
        }
    };

    private RetryableScope() {
        // intentionally left blank.
    }

    public static RetryableScope create() {
        return new RetryableScope();
    }

    /**
     * Replaces the default exception handler (including delay handling of retries) with a custom one.
     */
    public RetryableScope withExceptionHandler(Consumer<Exception> e) {
        this.onException = e;
        return this;
    }

    /**
     * Sets the timeout the default exception handler will delay retries in milliseconds. The default is 100ms.
     */
    public RetryableScope withDelay(long ms) {
        this.iterationTimeoutMs.set(ms);
        return this;
    }

    /**
     * Sets the amount of retries that the action will be re-called in case of an exception. The default is 10.
     */
    public RetryableScope withMaxRetries(long amount) {
        this.iterationCountMax.set(amount);
        return this;
    }

    /**
     * Perform the given action. Retry execution up to {@link #withMaxRetries(long)} trimes, with a delay of
     * {@link #withDelay(long)} milliseconds between retries (given the exception and timeout handler has not been replaced
     * using {@link #withExceptionHandler(Consumer)}).
     */
    public void run(Runnable action) {
        boolean madeIt = false;
        RuntimeException lastException = null;
        for (long i = 0; i < iterationCountMax.get(); ++i) {
            try {
                action.run();
                madeIt = true;
                break;
            } catch (RuntimeException e) {
                lastException = e;
                onException.accept(e);
            }
        }

        if (!madeIt) {
            throw lastException;
        }
    }

}

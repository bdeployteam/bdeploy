package io.bdeploy.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;

class RetryableScopeTest {

    @Test
    void testRetry() {
        assertThrows(IllegalStateException.class, () -> {
            RetryableScope.create().withMaxRetries(2).withDelay(2).run(() -> {
                throw new IllegalStateException("Always throw!");
            });
        }, "Always throw!");

        assertDoesNotThrow(() -> {
            AtomicLong count = new AtomicLong(2);
            RetryableScope.create().withMaxRetries(10).withDelay(2).run(() -> {
                if (count.getAndDecrement() <= 0) {
                    return;
                }

                throw new IllegalStateException("Boom!");
            });
        });

        LongAdder exceptions = new LongAdder();

        assertDoesNotThrow(() -> {
            AtomicLong count = new AtomicLong(2);
            RetryableScope.create().withMaxRetries(10).withExceptionHandler(e -> {
                exceptions.increment();
            }).run(() -> {
                if (count.getAndDecrement() <= 0) {
                    return;
                }

                throw new IllegalStateException("Boom!");
            });
        });

        assertEquals(2, exceptions.sum());
    }

}

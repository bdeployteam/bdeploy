package io.bdeploy.common.util;

import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Helper to aid in working with {@link Future}s
 */
public class FutureHelper {

    public static void awaitAll(Collection<Future<?>> futures) {
        futures.forEach(t -> {
            try {
                t.get();
            } catch (Exception e) {
                throw new IllegalStateException("asynchronous operation failed", e);
            }
        });
    }

}

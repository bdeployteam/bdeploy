package io.bdeploy.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Helper to aid in working with {@link Future}s
 */
public class FutureHelper {

    private FutureHelper() {
    }

    public static void awaitAll(Iterable<Future<?>> futures) {
        List<Throwable> exceptions = new ArrayList<>();

        futures.forEach(t -> {
            try {
                t.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                exceptions.add(ie);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        if (!exceptions.isEmpty()) {
            IllegalStateException ise = new IllegalStateException("Asynchronous operation(s) failed", exceptions.get(0));
            exceptions.subList(1, exceptions.size()).forEach(ise::addSuppressed);
            throw ise;
        }
    }
}

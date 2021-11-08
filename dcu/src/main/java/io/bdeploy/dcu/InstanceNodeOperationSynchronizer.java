package io.bdeploy.dcu;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHive.Operation;

/**
 * Manages potentially concurrent operations which are not allowed to be performed in parallel and should only be performed *once*
 * even if requested multiple times..
 */
public class InstanceNodeOperationSynchronizer {

    private final Map<Comparable<?>, Future<?>> poolOps = new TreeMap<>();

    public synchronized boolean isPerforming(Comparable<?> key) {
        return poolOps.containsKey(key);
    }

    /**
     * Performs the operation or waits until an existing operation with the same key is done
     *
     * @param key the operation key.
     * @param op the operation to be performed if not already running.
     */
    public void perform(Comparable<?> key, BHive hive, Operation<?> op) {
        Future<?> existing;
        CompletableFuture<?> tracker = null;
        synchronized (this) {
            existing = poolOps.get(key);

            if (existing == null) {
                tracker = new CompletableFuture<>();
                poolOps.put(key, tracker);
            }
        }

        if (existing != null) {
            try {
                existing.get(); // just wait for the existing one.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new IllegalStateException("Operation failed", e);
            }

            return;
        } else {
            try {
                hive.execute(op);
                tracker.complete(null);
            } catch (Exception e) {
                tracker.completeExceptionally(e);
                throw e;
            } finally {
                synchronized (this) {
                    poolOps.remove(key);
                }
            }
        }
    }

}

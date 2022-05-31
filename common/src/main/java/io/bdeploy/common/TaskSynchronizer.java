package io.bdeploy.common;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Performs tasks. When *another* Thread wants to perform the *same* operation, it instead waits for the first Thread to complete
 * the task.
 */
public class TaskSynchronizer {

    @SuppressWarnings("unchecked")
    private final Map<Comparable<?>, Future<?>> tasks = new TreeMap<>((a, b) -> {
        String clsAName = a.getClass().getName();
        String clsBName = b.getClass().getName();

        int x = clsAName.compareTo(clsBName);

        if (x != 0) {
            return x;
        }

        // only if SAME classes.
        return ((Comparable<Object>) a).compareTo(b);
    });

    /**
     * @param key the key to check
     * @return whether the given task key is currently associated with a running task.
     */
    public synchronized boolean isPerforming(Comparable<?> key) {
        return tasks.containsKey(key);
    }

    /**
     * Performs the operation or waits until an existing operation with the same key is done
     *
     * @param key the task key.
     * @param task the task to be performed if not already running.
     */
    @SuppressWarnings("unchecked")
    public <T> T perform(Comparable<?> key, Callable<T> task) {
        Future<?> existing;
        CompletableFuture<?> tracker = null;
        synchronized (this) {
            existing = tasks.get(key);

            if (existing == null) {
                tracker = new CompletableFuture<>();
                tasks.put(key, tracker);
            }
        }

        if (existing != null) {
            try {
                return (T) existing.get(); // just wait for the existing one.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Task interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Task failed", e);
            }
        } else {
            try {
                T result = task.call();
                tracker.complete(null);
                return result;
            } catch (RuntimeException e) {
                tracker.completeExceptionally(e);
                throw e;
            } catch (Exception e) {
                tracker.completeExceptionally(e);
                throw new RuntimeException(e);
            } finally {
                synchronized (this) {
                    tasks.remove(key);
                }
            }
        }
    }

}

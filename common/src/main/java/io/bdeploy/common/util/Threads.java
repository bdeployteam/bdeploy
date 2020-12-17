package io.bdeploy.common.util;

import java.util.function.BooleanSupplier;

/**
 * Common helpers for waiting and sleeping.
 */
public class Threads {

    private Threads() {
    }

    /**
     * Waits until notified or interrupted or the condition is met. The caller needs to be the owner of the lock.
     *
     * @return {@code false} if the thread was interrupted while waiting
     */
    public static boolean wait(Object lock, BooleanSupplier condition) {
        try {
            synchronized (lock) {
                while (condition.getAsBoolean()) {
                    lock.wait();
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Causes the current thread to sleep the given amount of time or until interrupted.
     *
     * @param millis the length of time to sleep in milliseconds
     * @return {@code false} if the thread was interrupted while sleeping
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}

/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.minion.tasks;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for threads to simplify common operations.
 */
public abstract class MinionThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MinionThread.class);

    private static final AtomicLong threadCount = new AtomicLong();

    private volatile boolean running;
    private volatile boolean wasRunning;

    /**
     * Creates the thread with a generated name.
     */
    protected MinionThread() {
        this(null);
    }

    /**
     * Creates the thread with the given name.
     *
     * @param threadName
     *            thread name; {@code null} generates the name
     */
    protected MinionThread(String threadName) {
        super(makeName(threadName, "MinionThread"));
    }

    /**
     * Generates a thread name.
     *
     * @param threadName
     *            default name; {@code null} generates the name
     * @param prefix
     *            prefix for the generated name
     * @return {@code threadName} or a generated name if {@code threadName} was {@code null}
     */
    protected static String makeName(String threadName, String prefix) {
        if (threadName == null) {
            threadName = prefix + '-' + threadCount.incrementAndGet() + '-';
        }
        return threadName;
    }

    /**
     * Does some general thread setup and calls {@link #doTheJob()}.
     */
    @Override
    public final void run() {
        if (isInterrupted()) {
            return;
        }

        // do the job
        try {
            running = true;
            wasRunning = true;
            doTheJob();
        } catch (Throwable tx) {
            log.error("thread {} failed", this, tx);
        } finally {
            running = false;
        }
    }

    /**
     * Returns {@code true} if this thread is running.
     */
    public final boolean isRunning() {
        return running;
    }

    /**
     * Waits at most <code>millis</code> milliseconds for this thread to run.
     * Note that calling this method doesn't ensure that this thread is running afterwards.
     *
     * @param millis
     *            the time to wait in milliseconds
     */
    public final void waitForRunning(final long millis) {
        try {
            final long base = System.currentTimeMillis();
            long delay = 10;
            while (!running && !wasRunning && isAlive()) {
                Thread.sleep(delay);
                delay = Math.max(delay * 2, 100);
                long now = System.currentTimeMillis() - base;
                if (now >= millis) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            // ignore it
        }
    }

    /**
     * Waits at most {@code millis} milliseconds for this thread to
     * die. A timeout of {@code 0} means to wait forever.
     *
     * @see Thread#join(long)
     */
    public final void waitForTermination(long millis) {
        try {
            super.join(millis);
        } catch (InterruptedException e) {
            // ignore it
        }
    }

    /**
     * Tries to stop this tread as soon as possible.
     * Only the owner of the thread should call this method.
     */
    public void terminate() {
        interrupt();
    }

    /**
     * Does whatever this thread should do.
     */
    protected abstract void doTheJob() throws Exception;

    @Override
    public String toString() {
        return getClass().getName() + '[' + getName() + ']';
    }

    /**
     * Tests if the specified exception/error or its cause
     * is either {@code java.lang.InterruptedException} or {@code java.io.InterruptedIOException}.
     *
     * @see Throwable#getCause()
     */
    public static boolean isInterruptedException(Throwable tx) {
        if (tx instanceof InterruptedException) {
            return true;
        }
        if (tx instanceof InterruptedIOException) {
            return true;
        }
        Throwable cause = tx.getCause();
        if (cause == null || cause == tx) {
            return false;
        }
        return isInterruptedException(cause);
    }

}

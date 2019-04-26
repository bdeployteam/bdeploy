/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.minion.tasks;

import com.google.common.base.Preconditions;

/**
 * Extension to {@link MinionLoopThread} for jobs which run in time intervals.
 */
public abstract class MinionIntervalThread extends MinionLoopThread {

    protected final long minDelay;
    protected final long interval;

    private volatile boolean isRunning;

    /**
     * Creates the thread with a generated name.
     *
     * @param interval
     *            interval in milliseconds
     * @param minDelay
     *            minimum delay in milliseconds
     */
    protected MinionIntervalThread(long interval, long minDelay) {
        this(null, interval, minDelay);
    }

    /**
     * Creates the thread with the given name.
     *
     * @param threadName
     *            thread name; {@code null} generates the name
     * @param interval
     *            interval in milliseconds
     * @param minDelay
     *            minimum delay in milliseconds
     */
    protected MinionIntervalThread(String threadName, long interval, long minDelay) {
        super(threadName);
        Preconditions.checkArgument(minDelay >= 0);
        Preconditions.checkArgument(interval >= minDelay);
        this.interval = interval;
        this.minDelay = minDelay;
    }

    @Override
    protected final void doLoopJob() throws Exception {
        final long start = System.currentTimeMillis();
        try {
            isRunning = true;
            doIntervalJob();
        } finally {
            isRunning = false;
            final long jobDuration = System.currentTimeMillis() - start;
            final long delay = interval - jobDuration;
            doIntervalFinish(Math.max(delay, minDelay));
        }
    }

    @Override
    public final void wakeup() {
        if (!isRunning) {
            super.wakeup();
        }
    }

    /**
     * Finishes this run of the loop. The standard implementation calls
     * {@link MinionLoopThread#doSleep(long)}.
     * <p>
     * Override this method to change the way how to finish the current loop.
     *
     * @param millis
     *            the time in milliseconds to finish the current interval
     */
    protected void doIntervalFinish(long millis) {
        doSleep(millis);
    }

    /**
     * Does the job of one interval.
     */
    protected abstract void doIntervalJob() throws Exception;

}

/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.minion.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for threads which runs operations in a loop. Call {@link #interrupt()} to stop the
 * loop.
 */
public abstract class MinionLoopThread extends MinionThread {

    private static final Logger log = LoggerFactory.getLogger(MinionLoopThread.class);

    /**
     * Defines the logic for 'stop' and 'wake up'.
     * 'stop' and 'wake up' are never set at the same time.
     */
    private static final class Stopper {

        /**
         * Set this attribute if the loop may not run again; this will also stop the thread.
         */
        private boolean stopped;

        /**
         * Set this attribute if we only want to interrupting 'sleep' and 'wait' operations.
         */
        private boolean doWakeup;

        /**
         * Returns the {@link #stopped} flag.
         */
        synchronized boolean isStopped() {
            return stopped;
        }

        /**
         * Resets the {@link #doWakeup} flag and the interrupted status of the thread.
         */
        synchronized void resetWakeup() {
            doWakeup = false;
            interrupted();
        }

        /**
         * Sets the {@link #stopped} flag if neither the {@link #doWakeup}, nor the {@link #stopped} flag were set.
         *
         * @return {@code true} if the {@link #stopped} flag is set
         */
        synchronized boolean markStopped() {
            if (stopped || doWakeup) {
                return false;
            }
            stopped = true;
            return true;
        }

        /**
         * Sets the {@link #doWakeup} flag if neither the {@link #doWakeup}, nor the {@link #stopped} flag were set.
         *
         * @return {@code true} if the {@link #doWakeup} flag is set
         */
        synchronized boolean wakeup() {
            if (stopped || doWakeup) {
                return false;
            }
            doWakeup = true;
            return true;
        }

        /**
         * Resets the {@link #doWakeup} flag and interrupts the thread.
         */
        synchronized void terminate(MinionLoopThread thread) {
            doWakeup = false;
            thread.interrupt();
        }

    }

    /**
     * Handles the stopping of this thread.
     */
    private final Stopper stopper = new Stopper();

    /** true if the doLoopJob() method is currently running */
    protected volatile boolean doLoopJobIsRunning;

    /** delay in milliseconds before the first run of the loop */
    private long delayFirstRun;

    /**
     * Creates the thread with a generated name.
     */
    protected MinionLoopThread() {
        super();
    }

    /**
     * Creates the thread with the given name.
     *
     * @param threadName
     *            thread name; {@code null} generates the name
     */
    protected MinionLoopThread(String threadName) {
        super(threadName);
    }

    /**
     * Sets a delay in milliseconds before the first run of the loop.
     */
    public final void setDelayFirstRun(long delayFirstRun) {
        this.delayFirstRun = delayFirstRun;
    }

    /**
     * Tests whether this thread has been stopped or interrupted.
     */
    public final boolean isStopped() {
        return stopper.isStopped() || isInterrupted();
    }

    @Override
    protected final void doTheJob() {
        if (isStopped()) {
            return;
        }

        try {
            prepareThread();
        } catch (Exception e) {
            if (isInterruptionFromOutside(e)) {
                return;
            }
            log.error("cannot prepare thread " + this, e);
            return;
        }

        if (delayFirstRun > 0) {
            doSleep(delayFirstRun);
        }

        try {
            runLoop();
        } finally {
            try {
                finishThread();
            } catch (Exception e) {
                log.error("cannot finish thread " + this, e);
            }
        }
    }

    private void runLoop() {
        while (!isStopped()) {
            try {
                stopper.resetWakeup();
                doLoopJobIsRunning = true;
                doLoopJob();
            } catch (Throwable e) {
                if (isInterruptionFromOutside(e)) {
                    markStopped();
                } else {
                    log.error("thread " + this + " failed", e);
                    doSleep(5000); // avoid hot error loops
                }
            } finally {
                doLoopJobIsRunning = false;
            }
        }
    }

    /**
     * Tests if the reason for an exception or error in a thread is an interruption from outside.
     * Runs {@link #interrupt()} in that case.
     *
     * @param tx exception or error
     * @return {@code true} if we got an interruption from outside
     */
    protected final boolean handleInterrupted(Throwable tx) {
        if (isInterruptionFromOutside(tx)) {
            if (!isStopped()) {
                interrupt();
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if some exception or error is an interruption from outside.
     */
    protected boolean isInterruptionFromOutside(Throwable tx) {
        return MinionThread.isInterruptedException(tx);
    }

    /**
     * Marks the thread, so that the loop will stop as soon as possible.
     */
    protected final void markStopped() {
        stopper.markStopped();
    }

    /**
     * Wakes up this thread by interrupting 'sleep' and 'wait' operations.
     */
    protected void wakeup() {
        if (stopper.wakeup()) {
            log.info("wake up " + getName());
            super.interrupt();
        }
    }

    @Override
    public final void terminate() {
        log.info("terminate " + getName());
        stopper.terminate(this);
    }

    /**
     * Stops the WAMAS thread. It's allowed to call this method multiple times.
     *
     * @see Thread#interrupt()
     */
    @Override
    public final void interrupt() {
        if (!isInterruptAllowed) {
            log.warn("Interrupt skipped.");
            return;
        }

        if (!stopper.markStopped()) {
            return;
        }

        log.info("interrupt: " + getName());

        try {
            interruptThread();
        } catch (InterruptedException e) {
            // ignore
        } catch (Exception e) {
            log.error("interrupt failed", e);
        } finally {
            super.interrupt();
        }
    }

    private volatile boolean isInterruptAllowed = true;

    public void setInterruptAllowed(boolean isInterruptAllowed) {
        this.isInterruptAllowed = isInterruptAllowed;
        if (log.isDebugEnabled()) {
            log.debug(String.format("setInterruptAllowed(%s) called on %s", isInterruptAllowed, getName()));
        }
    }

    /**
     * Like {@link Thread#sleep(long)} but stops the thread instead of throwing an
     * {@code InterruptedException}.
     * <p>
     * Please note: this is simply a helper for doing a 'sleep'. Never override this method.
     */
    protected final void doSleep(long millis) {
        try {
            sleep(millis);
        } catch (InterruptedException e) {
            markStopped();
        }
    }

    /**
     * This method is called once at the start of the thread.
     */
    protected void prepareThread() throws Exception {
        // empty
    }

    /**
     * This method is called once at the end of the thread.
     */
    protected void finishThread() throws Exception {
        // empty
    }

    /**
     * Requests the thread to stop. It is allowed to call this method multiple times.
     * <p>
     * Use this method to terminate other threads (owned by this thread).
     */
    protected void interruptThread() throws Exception {
        // empty
    }

    /**
     * Does the job; runs inside the loop until the thread stops.
     */
    protected abstract void doLoopJob() throws Exception;

}

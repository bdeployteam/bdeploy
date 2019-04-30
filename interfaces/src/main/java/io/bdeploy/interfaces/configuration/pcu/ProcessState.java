package io.bdeploy.interfaces.configuration.pcu;

/**
 * Enumeration containing the possible states of a process.
 */
public enum ProcessState {

    /** Process is not running. */
    STOPPED,

    /** Recovering of the process failed */
    STOPPED_CRASHED,

    /** Process is running. */
    RUNNING,

    /** Process was restarted after a crash. State remains unstable for a while. */
    RUNNING_UNSTABLE,

    /** Process was running but crashed unexpectedly. Will automatically be restarted after some delay */
    CRASH_BACK_OFF;

    /**
     * Returns whether or not the status indicates that the process is alive and running.
     *
     * @return {@code true} if it is running
     */
    public boolean isRunning() {
        return this == RUNNING || this == RUNNING_UNSTABLE;
    }

    /**
     * Returns whether or not the status indicates that the process is alive and running
     * or that it is scheduled to be started in the future.
     *
     * @return {@code true} if it is running
     */
    public boolean isRunningOrScheduled() {
        return this == RUNNING || this == RUNNING_UNSTABLE || this == CRASH_BACK_OFF;
    }

}
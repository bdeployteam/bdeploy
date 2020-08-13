package io.bdeploy.interfaces.configuration.pcu;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Enumeration containing the possible states of a process.
 */
public enum ProcessState {

    /**
     * Process is not running.
     * Indicates the initial state or that the process has been manually stopped or that it terminated expectedly.
     */
    @JsonEnumDefaultValue
    STOPPED,

    /**
     * Process is running.
     */
    RUNNING,

    /**
     * Process is running.
     * Indicates that the process was automatically restarted due to a crash.
     */
    RUNNING_UNSTABLE,

    /**
     * Process is running.
     * Indicates that stopping is currently in progress or planned in near future.
     */
    RUNNING_STOP_PLANNED,

    /**
     * Process is stopped.
     * Indicates that the process control failed to recover the process as it keeps crashing.
     */
    CRASHED_PERMANENTLY,

    /**
     * Process is stopped.
     * Indicates that the process controls will automatically restart the process after some delay.
     */
    CRASHED_WAITING;

    /**
     * Returns whether or not the status indicates that the process is alive and running.
     *
     * @return {@code true} if it is running
     */
    public boolean isRunning() {
        return this == RUNNING || this == RUNNING_UNSTABLE || this == RUNNING_STOP_PLANNED;
    }

    /**
     * Returns whether or not the status indicates that the process is stopped and that it is not planned
     * to automatically start it.
     *
     * @return {@code true} if it is stopped
     */
    public boolean isStopped() {
        return this == STOPPED || this == CRASHED_PERMANENTLY;
    }

    /**
     * Returns whether or not the status indicates that the process is alive and running
     * or that it is scheduled to be started in the future.
     *
     * @return {@code true} if it is running
     */
    public boolean isRunningOrScheduled() {
        return this == RUNNING || this == RUNNING_UNSTABLE || this == CRASHED_WAITING || this == RUNNING_STOP_PLANNED;
    }

}
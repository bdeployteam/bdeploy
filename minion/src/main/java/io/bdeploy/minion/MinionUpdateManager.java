package io.bdeploy.minion;

import io.bdeploy.interfaces.remote.MinionUpdateResource;

/**
 * Allows swapping the minions actual update logic (usually exiting the JVM after a short timeout with a special exit code).
 *
 * @see MinionUpdateResource
 */
@FunctionalInterface
public interface MinionUpdateManager {

    /**
     * Trigger update of the minion. This would usually involve shutting down the JVM with a special exist code to tell a script
     * to move the new software version from a special place and start the new one.
     *
     * @param timeout time in milliseconds to delay asynchronously before updating. This allows currently running operations (as
     *            the remote update operation itself) to finish.
     */
    public void performUpdate(long timeout);

}

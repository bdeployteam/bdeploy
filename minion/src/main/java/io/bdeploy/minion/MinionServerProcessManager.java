package io.bdeploy.minion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.remote.MinionUpdateResource;

/**
 * Allows swapping the minions actual restart logic (usually exiting the JVM after a short timeout with a special exit code).
 *
 * @see MinionUpdateResource
 */
public interface MinionServerProcessManager {

    /**
     * Trigger update of the minion. This would usually involve shutting down the JVM with a special exist code to tell a script
     * to move the new software version from a special place and start the new one.
     *
     * @param timeout time in milliseconds to delay asynchronously before updating. This allows currently running operations (as
     *            the remote update operation itself) to finish.
     */
    public void performRestart(long timeout);

    /**
     * Trigger a shutdown of the server. Use with care - the server will not be restarted automatically.
     *
     * @param timeout time in milliseconds to delay the shutdown. This allows concurrently running requests to finish (i.e. the
     *            request which requested shutdown).
     */
    public void performShutdown(long timeout);

    public static class NoopServerProcessManager implements MinionServerProcessManager {

        private static final Logger log = LoggerFactory.getLogger(NoopServerProcessManager.class);

        @Override
        public void performRestart(long timeout) {
            log.error("No server process manager configured.");
        }

        @Override
        public void performShutdown(long timeout) {
            log.error("No server process manager configured.");
        }
    }
}

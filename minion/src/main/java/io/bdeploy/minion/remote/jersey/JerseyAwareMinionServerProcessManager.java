package io.bdeploy.minion.remote.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionServerProcessManager;

/**
 * A {@link MinionServerProcessManager} which knows how to shut down a {@link JerseyServer} before exiting the JVM.
 */
public class JerseyAwareMinionServerProcessManager implements MinionServerProcessManager {

    private static final Logger log = LoggerFactory.getLogger(JerseyAwareMinionServerProcessManager.class);
    private final JerseyServer server;
    private final MinionRoot root;

    public JerseyAwareMinionServerProcessManager(JerseyServer server, MinionRoot root) {
        this.server = server;
        this.root = root;
    }

    @Override
    public void performRestart(long timeout) {
        // before we restart, we'll try to create a stack dump for later reference.
        Threads.dump(root.getLogDir(), "Restart-Threads.dump");

        // Now restart async after a given timeout, and return to the caller.
        Thread.ofVirtual().start(() -> {
            Threads.sleep(timeout);
            log.info("Restarting Server");

            server.close();
            System.exit(UpdateHelper.CODE_RESTART);
        });
    }

    @Override
    public void performShutdown(long timeout) {
        // dump threads so we can see the server state before shutdown.
        Threads.dump(root.getLogDir(), "Shutdown-Threads.dump");

        // Async to allow callers to finish.
        Thread.ofVirtual().start(() -> {
            Threads.sleep(timeout);
            log.info("Shutting down Server");

            server.close();
            System.exit(0);
        });
    }
}

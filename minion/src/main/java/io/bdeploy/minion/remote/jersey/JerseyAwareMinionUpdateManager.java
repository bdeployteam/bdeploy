package io.bdeploy.minion.remote.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionUpdateManager;

/**
 * A {@link MinionUpdateManager} which knows how to shut down a {@link JerseyServer} before exiting the JVM.
 */
@SuppressFBWarnings("DM_EXIT")
public class JerseyAwareMinionUpdateManager implements MinionUpdateManager {

    /**
     * This must match what the launcher script(s) expect to perform an update.
     */
    private static final int CODE_UPDATE = 42;
    private static final Logger log = LoggerFactory.getLogger(JerseyAwareMinionUpdateManager.class);
    private final JerseyServer server;

    public JerseyAwareMinionUpdateManager(JerseyServer server) {
        this.server = server;
    }

    @Override
    public void performUpdate(long timeout) {
        Thread updateThread = new Thread(() -> {
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                // not happening xD
                Thread.currentThread().interrupt();
            }

            log.info("Exiting to perform update");

            server.close();
            System.exit(CODE_UPDATE);
        });

        updateThread.setDaemon(false);
        updateThread.start();
    }

}

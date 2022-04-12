package io.bdeploy.minion.remote.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRestartManager;

/**
 * A {@link MinionRestartManager} which knows how to shut down a {@link JerseyServer} before exiting the JVM.
 */
@SuppressFBWarnings("DM_EXIT")
public class JerseyAwareMinionRestartManager implements MinionRestartManager {

    private static final Logger log = LoggerFactory.getLogger(JerseyAwareMinionRestartManager.class);
    private final JerseyServer server;

    public JerseyAwareMinionRestartManager(JerseyServer server) {
        this.server = server;
    }

    @Override
    public void performRestart(long timeout) {
        Thread updateThread = new Thread(() -> {
            Threads.sleep(timeout);
            log.info("Exiting to perform update");

            server.close();
            System.exit(UpdateHelper.CODE_RESTART);
        });

        updateThread.setDaemon(false);
        updateThread.start();
    }

}

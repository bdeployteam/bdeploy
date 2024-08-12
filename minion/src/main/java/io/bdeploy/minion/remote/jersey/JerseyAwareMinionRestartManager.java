package io.bdeploy.minion.remote.jersey;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRestartManager;
import io.bdeploy.minion.MinionRoot;

/**
 * A {@link MinionRestartManager} which knows how to shut down a {@link JerseyServer} before exiting the JVM.
 */
public class JerseyAwareMinionRestartManager implements MinionRestartManager {

    private static final Logger log = LoggerFactory.getLogger(JerseyAwareMinionRestartManager.class);
    private final JerseyServer server;
    private final MinionRoot root;

    public JerseyAwareMinionRestartManager(JerseyServer server, MinionRoot root) {
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
            log.info("Exiting to perform update");

            server.close();
            System.exit(UpdateHelper.CODE_RESTART);
        });
    }

}

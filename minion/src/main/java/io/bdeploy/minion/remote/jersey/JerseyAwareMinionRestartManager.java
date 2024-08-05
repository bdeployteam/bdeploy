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

    private static void cycleDumps(Path name) {
        // reverse move files to keep max 4 (three numbered 1 through 3).
        for (int i = 2; i >= 0; --i) {
            rollOverFiles(name, i);
        }
    }

    private static void rollOverFiles(Path filePath, int count) {
        Path parent = filePath.getParent();
        String fileName = filePath.getFileName().toString();

        Path source = parent.resolve(fileName + (count > 0 ? ("." + count) : ""));
        if (!Files.exists(source)) {
            return;
        }

        Path target = parent.resolve(fileName + "." + (count + 1));
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Cannot move {} to {}", source, target);
        }
    }

    @Override
    public void performRestart(long timeout) {
        // before we restart, we'll try to create a stack dump for later reference.
        Thread dumpThread = Thread.ofVirtual().start(() -> {
            Path outputName = root.getLogDir().resolve("Restart-Threads.dump");
            cycleDumps(outputName);
            try (PrintWriter pw = new PrintWriter(Files.newOutputStream(outputName))) {
                ThreadMXBean mxb = ManagementFactory.getThreadMXBean();
                for (ThreadInfo i : mxb.dumpAllThreads(true, true)) {
                    pw.append(i.toString());
                }
            } catch (Exception e) {
                log.warn("Cannot create thread dump before restart.", e);
            }
        });

        // give the trace a maximum of 10 seconds time.
        try {
            dumpThread.join(Duration.ofSeconds(10));
        } catch (InterruptedException e) {
            // ignored, we want to restart anyhow!
        }

        // Now restart async after a given timeout, and return to the caller.
        Thread.ofVirtual().start(() -> {
            Threads.sleep(timeout);
            log.info("Exiting to perform update");

            server.close();
            System.exit(UpdateHelper.CODE_RESTART);
        });
    }

}

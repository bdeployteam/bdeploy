package io.bdeploy.minion.tasks;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.bdeploy.common.util.PathHelper;

/**
 * A periodic task that cleans top-level files and directories that are older than one hour.
 */
public class CleanupDownloadDirTask extends MinionIntervalThread {

    private static final long CLEANUP_INTERVAL = TimeUnit.MINUTES.toMillis(10);
    private final Path downloadDir;

    public CleanupDownloadDirTask(Path downloadDir) {
        super("CleanupDownloadDir", CLEANUP_INTERVAL, CLEANUP_INTERVAL / 2);
        this.downloadDir = downloadDir;
    }

    @Override
    protected void doIntervalJob() throws Exception {
        try (Stream<Path> paths = Files.list(downloadDir)) {
            paths.forEach(CleanupDownloadDirTask::checkAndDelete);
        }
    }

    /**
     * Deletes this file or folder if it is older than one hour.
     *
     * @param path
     *            the path to delete
     */
    private static void checkAndDelete(Path path) {
        long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        File file = path.toFile();
        if (file.lastModified() > oneHourAgo) {
            return;
        }
        PathHelper.deleteRecursive(path);
    }

}

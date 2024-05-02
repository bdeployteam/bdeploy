package io.bdeploy.launcher.cli.ui.browser.workers;

import java.nio.file.Path;
import java.util.List;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.LockDirectoryOperation;
import io.bdeploy.bhive.op.ReleaseDirectoryLockOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.util.ExceptionHelper;
import io.bdeploy.launcher.cli.ui.MessageDialogs;
import io.bdeploy.launcher.cli.ui.browser.PropertyChangeActivityReporter;
import io.bdeploy.logging.audit.RollingFileAuditor;

/**
 * Base class that performs some work on one or more hives.
 */
public abstract class HiveTask extends SwingWorker<String, Void> {

    private static final Logger log = LoggerFactory.getLogger(HiveTask.class);

    protected final List<Path> hives;
    protected final Auditor auditor;
    protected final StringBuilder builder = new StringBuilder();

    protected HiveTask(List<Path> hives, Auditor auditor) {
        this.hives = hives;
        this.auditor = auditor;
    }

    @Override
    protected String doInBackground() throws Exception {
        int i = 0;
        log.info("Executing '{}'", getTaskName());
        for (Path hiveDir : hives) {
            Path rootDir = hiveDir.getParent();
            builder.append(hiveDir.toString()).append("\n");
            try (BHive hive = new BHive(hiveDir.toUri(),
                    auditor != null ? auditor : RollingFileAuditor.getFactory().apply(hiveDir), getActivityReporter())) {
                try {
                    setProgress(i++);
                    hive.execute(new LockDirectoryOperation().setDirectory(rootDir));
                    doExecute(hive);
                } catch (Exception ex) {
                    builder.append("Failed: " + ExceptionHelper.mapExceptionCausesToReason(ex));
                } finally {
                    hive.execute(new ReleaseDirectoryLockOperation().setDirectory(rootDir));
                }
            }
            builder.append("\n");
        }
        String result = builder.toString();
        log.info("Done: \n{}", result);
        return result;
    }

    @Override
    protected void done() {
        String result;
        try {
            result = get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            result = ExceptionHelper.mapExceptionCausesToReason(ie);
        } catch (Exception e) {
            result = ExceptionHelper.mapExceptionCausesToReason(e);
        }
        MessageDialogs.showDetailedMessage(result);
    }

    /**
     * Returns the name of the task. Used for logging.
     */
    protected abstract String getTaskName();

    /**
     * Executes the task on the given hive.
     */
    protected abstract void doExecute(BHive hive);

    /**
     * Returns the activity reporter to use.
     */
    protected ActivityReporter getActivityReporter() {
        return new PropertyChangeActivityReporter(getPropertyChangeSupport());
    }

}

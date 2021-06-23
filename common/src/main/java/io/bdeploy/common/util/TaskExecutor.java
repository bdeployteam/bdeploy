package io.bdeploy.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Simplifies execution and progress reporting for operations that should run in parallel.
 */
public class TaskExecutor {

    private final ActivityReporter reporter;
    private final List<Runnable> tasks = new ArrayList<>();

    /**
     * Creates a new executor using the given reporter
     */
    public TaskExecutor(ActivityReporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Adds the given task to the list of task.
     */
    public void add(Runnable task) {
        tasks.add(task);
    }

    /**
     * Executes all tasks and waits for completion.
     *
     * @param taskName the activity that should be reported
     */
    public void run(String taskName) {
        ExecutorService service = Executors.newCachedThreadPool(new NamedDaemonThreadFactory(() -> "Task-" + taskName));
        try (Activity activity = reporter.start(taskName, tasks.size())) {
            // Submit all task for execution
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(service.submit(new ActivityRunnable(activity, task)));
            }

            // Block and wait for all to complete
            RuntimeException failure = null;
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    if (failure == null) {
                        failure = new RuntimeException("Failed to execute task");
                        failure.addSuppressed(ie);
                    }
                } catch (Exception ex) {
                    if (failure == null) {
                        failure = new RuntimeException("Failed to execute task");
                        failure.addSuppressed(ex);
                    }
                }
            }

            // throw an error in case that one or more task failed
            if (failure != null) {
                throw failure;
            }
        } finally {
            service.shutdown();
        }
    }

    /**
     * A runnable that increments the given activity when done.
     */
    private class ActivityRunnable implements Runnable {

        private final Activity activity;
        private final Runnable task;

        public ActivityRunnable(Activity activity, Runnable task) {
            this.activity = activity;
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
            activity.worked(1);
        }
    }

}

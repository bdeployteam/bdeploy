package io.bdeploy.launcher.cli.branding;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

import io.bdeploy.common.ActivityReporter;

public class LauncherSplashReporter implements ActivityReporter {

    private final Deque<SplashActivity> activityStack = new ArrayDeque<>();
    private final LauncherSplashDisplay display;
    private final ScheduledExecutorService updater = Executors.newScheduledThreadPool(1);

    public LauncherSplashReporter(LauncherSplashDisplay display) {
        this.display = display;

        updater.scheduleAtFixedRate(this::update, 10, 10, TimeUnit.MILLISECONDS);
    }

    private void update() {
        if (activityStack.isEmpty()) {
            display.setStatusText("Loading...");
            display.setProgressMax(0);
            display.repaint();
            return;
        }

        SplashActivity current = activityStack.peek();
        if (current == null) {
            return;
        }

        display.setStatusText(current.activity);
        display.setProgressMax((int) current.getMaxAmount());
        display.setProgressCurrent((int) current.getCurrentAmount());
        display.repaint();
    }

    @Override
    public Activity start(String activity) {
        return start(activity, -1);
    }

    @Override
    public Activity start(String activity, long maxWork) {
        return start(activity, () -> maxWork, null);
    }

    @Override
    public Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
        SplashActivity splashActivity = new SplashActivity(activity, maxValue, currentValue);
        splashActivity.pushToStack();
        return splashActivity;
    }

    private final class SplashActivity implements Activity {

        private final LongSupplier maxAmount;
        private final LongSupplier currentAmount;
        private final long startTime;
        private final LongAdder localCurrent = new LongAdder();

        private long stopTime;
        private String activity;

        public SplashActivity(String activity, LongSupplier maxValue, LongSupplier currentValue) {
            this.activity = activity;
            this.maxAmount = maxValue;
            this.currentAmount = currentValue != null ? currentValue : localCurrent::sum;
            this.startTime = System.currentTimeMillis();
        }

        void pushToStack() {
            activityStack.push(this);
        }

        long getCurrentAmount() {
            if (stopTime != 0 && maxAmount.getAsLong() > 0) {
                return maxAmount.getAsLong(); // already stopped.
            }
            return currentAmount.getAsLong();
        }

        long getMaxAmount() {
            return maxAmount.getAsLong();
        }

        @Override
        public boolean isCancelRequested() {
            return false; // no cancel support for streams
        }

        @Override
        public void worked(long amount) {
            localCurrent.add(amount);
        }

        @Override
        public void activity(String activity) {
            this.activity = activity;
        }

        @Override
        public void done() {
            stopTime = System.currentTimeMillis();
            activityStack.remove(this);
        }

        @Override
        public long duration() {
            return (stopTime > 0 ? stopTime : System.currentTimeMillis()) - startTime;
        }

    }

    public void stop() {
        updater.shutdownNow();
    }
}

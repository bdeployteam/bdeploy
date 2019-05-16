/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.common;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.ws.rs.sse.InboundSseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.NamedDaemonThreadFactory;

/**
 * Allows reporting and tracking of activities.
 */
public interface ActivityReporter {

    /**
     * Start an {@link Activity} without any associated work amount.
     */
    public Activity start(String activity);

    /**
     * Start an {@link Activity} with a static work amount. Use
     * {@link Activity#worked(long)} to updated the already consumed work amount.
     * <p>
     * Special maxWork values:
     * <ul>
     * <li>-1: don't report work at all. only time will be reported
     * <li>0: don't report any maximum, just report current amount if possible.
     * </ul>
     */
    public Activity start(String activity, long maxWork);

    /**
     * Start an {@link Activity} with a dynamic maximum and current work amount. The
     * {@link Supplier}s are queried periodically when reporting progress, to catch
     * updates on the values.
     */
    public Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue);

    /**
     * Proxy remote activities to this {@link ActivityReporter} implementation if possible.
     */
    public NoThrowAutoCloseable proxyActivities(RemoteService service);

    /**
     * An Activity allows updating progress and signaling when the {@link Activity}
     * is finished.
     */
    public interface Activity extends NoThrowAutoCloseable {

        /**
         * Update the local work amount. Has no effect if the {@link Activity} was
         * started using a dynamic amount {@link Supplier}.
         */
        public void worked(long amount);

        /**
         * Signals that this {@link Activity} is done, removing it from the managing
         * {@link ActivityReporter}.
         */
        public void done();

        /**
         * Return the amount of milliseconds since the activity has been started.
         */
        public long duration();

        /**
         * @return whether cancellation is requested
         */
        public boolean isCancelRequested();

        public default void workAndCancelIfRequested(long amount) {
            worked(amount);
            if (isCancelRequested()) {
                done(); // make sure the activity is actually finished.
                throw new ActivityCancelledException();
            }
        }

        @Override
        default void close() {
            done();
        }

    }

    /**
     * Exception to be thrown when an activity has been cancelled from outside.
     */
    public static class ActivityCancelledException extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    /**
     * An {@link ActivityReporter} implementation which reports progress to a given
     * {@link PrintStream}.
     */
    public static final class Stream implements ActivityReporter {

        private static final Logger log = LoggerFactory.getLogger(Stream.class);

        private final PrintStream stream;
        private ScheduledExecutorService updater;
        private ScheduledFuture<?> scheduled;
        private boolean verbose;
        private BiFunction<RemoteService, Consumer<InboundSseEvent>, NoThrowAutoCloseable> proxyConnector;

        private final Deque<AsyncActivity> activities = new ArrayDeque<>();
        private final List<AsyncActivity> allActivities = new ArrayList<>();

        private String lastReportedActivity = "init";
        private long lastReportedAmount;

        public Stream(PrintStream stream) {
            this.stream = stream;
        }

        /**
         * A verbose summary will include more details about single activity
         * duration(s).
         */
        public void setVerboseSummary(boolean verbose) {
            this.verbose = verbose;
        }

        /**
         * Begins the reporting process. Reporting runs asynchronously (periodically) to
         * avoid stalling of running actions while catching up with dynamic updates to
         * progress values.
         */
        public void beginReporting() {
            if (updater == null) {
                updater = Executors
                        .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory(() -> "Activity Reporter (Console)"));
            }
            scheduled = updater.scheduleAtFixedRate(() -> catchAll(this::report), 0, 200, TimeUnit.MILLISECONDS);
        }

        /**
         * Stops (and cleans up resources associated with) progress reporting. No
         * further {@link Activity} processing will be done after this method finishes.
         * All remaining {@link Activity} instances are cleared (but not finished).
         */
        public void stopReporting() {
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            scheduled = null;
            if (updater != null) {
                updater.shutdownNow();
                updater = null;
            }
            reportSummary();

            activities.clear();
            stream.flush();
        }

        private void catchAll(Runnable x) {
            try {
                x.run();
            } catch (Exception e) {
                if (log.isTraceEnabled()) {
                    log.trace("Exception in stream reporter", e);
                }
            }
        }

        private synchronized void report() {
            AsyncActivity current = activities.peek();

            if (current == null) {
                return;
            }

            Long currentAmount = current.getCurrentAmount();
            boolean activityChanged = lastReportedActivity != null && !current.activity.equals(lastReportedActivity);
            boolean amountChanged = currentAmount != lastReportedAmount;

            if (!activityChanged && !amountChanged) {
                return;
            }

            stream.print('\r');

            Long max = current.getMaxAmount();
            if (max < 0) {
                stream.print(String.format("[%1$08d] %2$-70s", current.duration(), current.activity));
            } else if (max == 0) {
                stream.print(
                        String.format("[%1$08d] %2$-70s         /%3$8d", current.duration(), current.activity, currentAmount));
            } else {
                stream.print(
                        String.format("[%1$08d] %2$-70s %3$8d/%4$8d", current.duration(), current.activity, currentAmount, max));
            }
        }

        private synchronized void reportDone(AsyncActivity act) {
            stream.print('\r');

            Long max = act.getMaxAmount();
            if (max < 0) {
                stream.print(String.format("[%1$08d] %2$-70s     DONE%n", act.duration(), act.activity));
            } else if (max == 0) {
                stream.print(
                        String.format("[%1$08d] %2$-70s     DONE/%3$8d%n", act.duration(), act.activity, act.getCurrentAmount()));
            } else {
                stream.print(String.format("[%1$08d] %2$-70s     DONE/%3$8d%n", act.duration(), act.activity, max));
            }
            stream.flush();
            lastReportedActivity = null; // avoid newline on next report :)
        }

        private void reportSummary() {
            if (allActivities.isEmpty()) {
                return;
            }

            if (verbose) {
                stream.println();
                stream.println(String.format("%1$-81s %2$s", "ACTIVITY", "DURATION"));
                stream.println(repeat("=", 90));
                for (AsyncActivity act : allActivities) {
                    if (act.isNested) {
                        stream.println(String.format("  %1$-79s %2$8d ms", act.activity, act.duration()));
                    } else {
                        stream.println(String.format("%1$-81s %2$8d ms", act.activity, act.duration()));
                    }
                }
            }
        }

        private static String repeat(String s, int n) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < n; ++i) {
                builder.append(s);
            }
            return builder.toString();
        }

        @Override
        public Activity start(String activity) {
            return start(activity, -1);
        }

        @Override
        public synchronized Activity start(String activity, long maxWork) {
            return start(activity, () -> maxWork, null);
        }

        @Override
        public synchronized Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
            AsyncActivity act = new AsyncActivity(activity, maxValue, currentValue);
            activities.push(act);
            allActivities.add(act);
            return act;
        }

        private void done(AsyncActivity act) {
            activities.remove(act);
            reportDone(act);
        }

        private final class AsyncActivity implements ActivityReporter.Activity {

            private final String activity;
            private final LongAdder localCurrent = new LongAdder();
            private final LongSupplier currentAmount;
            private final LongSupplier maxAmount;
            private final long startTime;
            private long stopTime;
            private boolean isNested = false;

            public AsyncActivity(String activity, LongSupplier maxValue, LongSupplier currentValue) {
                this.activity = activity;
                this.maxAmount = maxValue;
                this.currentAmount = currentValue != null ? currentValue : localCurrent::sum;
                this.startTime = System.currentTimeMillis();
                this.isNested = !activities.isEmpty();
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
            public void done() {
                stopTime = System.currentTimeMillis();
                ActivityReporter.Stream.this.done(this);
            }

            @Override
            public long duration() {
                return (stopTime > 0 ? stopTime : System.currentTimeMillis()) - startTime;
            }

        }

        public void setProxyConnector(BiFunction<RemoteService, Consumer<InboundSseEvent>, NoThrowAutoCloseable> proxyConnector) {
            this.proxyConnector = proxyConnector;
        }

        @Override
        public NoThrowAutoCloseable proxyActivities(RemoteService service) {
            return new StreamRemoteActivityProxy(service);
        }

        private class StreamRemoteActivityProxy implements NoThrowAutoCloseable {

            private NoThrowAutoCloseable source;

            public StreamRemoteActivityProxy(RemoteService svc) {
                // getKeyStore is only set for real remotes. local hives will have no key store.
                if (svc != null && svc.getKeyStore() != null && proxyConnector != null) {
                    source = proxyConnector.apply(svc, this::onMessage);
                }
            }

            private void onMessage(InboundSseEvent event) {
                List<ActivitySnapshot> activities = event.readData(ActivitySnapshot.LIST_TYPE);
                for (ActivitySnapshot act : activities) {
                    stream.println("SRV: " + act);
                }
            }

            @Override
            public void close() {
                if (source != null) {
                    source.close();
                }
            }

        }
    }

    /**
     * No-op {@link ActivityReporter}, which does nothing, except being
     * <code>null</code>.
     */
    public static class Null implements ActivityReporter {

        @Override
        public Activity start(String activity) {
            return new NullActivity();
        }

        @Override
        public Activity start(String activity, long maxWork) {
            return new NullActivity();
        }

        @Override
        public Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
            return new NullActivity();
        }

        private static final class NullActivity implements ActivityReporter.Activity {

            long startTime = System.currentTimeMillis();

            @Override
            public void worked(long amount) {
                // nothing
            }

            @Override
            public void done() {
                // nothing
            }

            @Override
            public long duration() {
                return System.currentTimeMillis() - startTime;
            }

            @Override
            public boolean isCancelRequested() {
                return false;
            }

        }

        @Override
        public NoThrowAutoCloseable proxyActivities(RemoteService service) {
            return () -> {
            };
        }

    }

    /**
     * Delegator {@link ActivityReporter} which can change destination lazily once a proper instance
     * is available (after server startup, ...).
     */
    public class Delegating implements ActivityReporter {

        private ActivityReporter delegate = new ActivityReporter.Null();

        public void setDelegate(ActivityReporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public Activity start(String activity) {
            return delegate.start(activity);
        }

        @Override
        public Activity start(String activity, long maxWork) {
            return delegate.start(activity, maxWork);
        }

        @Override
        public Activity start(String activity, LongSupplier maxValue, LongSupplier currentValue) {
            return delegate.start(activity, maxValue, currentValue);
        }

        @Override
        public NoThrowAutoCloseable proxyActivities(RemoteService service) {
            return delegate.proxyActivities(service);
        }

    }

}

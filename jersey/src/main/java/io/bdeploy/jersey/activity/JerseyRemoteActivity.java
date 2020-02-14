package io.bdeploy.jersey.activity;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.ActivitySnapshot;
import io.bdeploy.common.util.UuidHelper;

final class JerseyRemoteActivity implements Activity {

    private static final Logger log = LoggerFactory.getLogger(JerseyRemoteActivity.class);

    private final String name;
    private final Consumer<JerseyRemoteActivity> onDone;
    private final Consumer<JerseyRemoteActivity> onCancel;
    private final LongSupplier maxWork;
    private final LongSupplier currentWork;
    private final long start;
    private final String uuid;
    private String parentUuid;
    private final List<String> scope;
    private long stop = 0;
    private boolean cancel = false;

    private final LongAdder localCurrent = new LongAdder();

    private final String user;

    public JerseyRemoteActivity(Consumer<JerseyRemoteActivity> onDone, String name, LongSupplier maxWork, LongSupplier currentWork,
            List<String> scope, String user) {
        this.onDone = onDone;
        this.name = name;
        this.maxWork = maxWork;
        this.currentWork = currentWork != null ? currentWork : localCurrent::sum;
        this.start = System.currentTimeMillis();
        this.uuid = UuidHelper.randomId();
        this.scope = scope;
        this.user = user;
        this.onCancel = null; // not required for local activities

        // wire activities by UUID. this is done so that serialization of activity "trees" stays
        // as flat as it is - otherwise too much traffic to clients would be produced. Clients
        // need to convert the flat list of activities to a tree representation when interested.
        JerseyRemoteActivity parent = JerseyBroadcastingActivityReporter.currentActivity.get();
        if (parent != null) {
            this.parentUuid = parent.uuid;
        }
        JerseyBroadcastingActivityReporter.currentActivity.set(this);

        if (log.isTraceEnabled()) {
            log.trace("Begin: [{}] {}", uuid, name);
        }
    }

    /**
     * Directly create an activity - used by {@link JerseyRemoteActivityProxy}
     */
    JerseyRemoteActivity(Consumer<JerseyRemoteActivity> onDone, Consumer<JerseyRemoteActivity> onCancel, String name, LongSupplier maxWork,
            LongSupplier currentWork, List<String> scope, String user, long start, String uuid, String parentUuid) {
        this.onDone = onDone;
        this.onCancel = onCancel;
        this.name = name;
        this.maxWork = maxWork;
        this.currentWork = currentWork;
        this.start = start;
        this.uuid = uuid;
        this.scope = scope;
        this.user = user;
        this.parentUuid = parentUuid;
    }

    @Override
    public void worked(long amount) {
        localCurrent.add(amount);
    }

    @Override
    public void done() {
        stop = System.currentTimeMillis();
        onDone.accept(this);

        if (log.isTraceEnabled()) {
            log.trace("Done: [{}] {}. Duration: {} ms", uuid, name, duration());
        }
    }

    @Override
    public long duration() {
        if (stop == 0) {
            return 0;
        }
        return stop - start;
    }

    @Override
    public boolean isCancelRequested() {
        return cancel;
    }

    void requestCancel() {
        this.cancel = true;

        if (onCancel != null) {
            onCancel.accept(this);
        }

        if (log.isTraceEnabled()) {
            log.trace("Cancel: [{}] {}, duration: {} ms", uuid, name, duration());
        }
    }

    String getUuid() {
        return this.uuid;
    }

    String getParentUuid() {
        return this.parentUuid;
    }

    String getUser() {
        return this.user;
    }

    @Override
    public String toString() {
        return "[" + this.uuid + "] " + this.name;
    }

    ActivitySnapshot snapshot() {
        return new ActivitySnapshot(uuid, name, stop != 0 ? duration() : (System.currentTimeMillis() - start),
                maxWork.getAsLong(), currentWork.getAsLong(), this.scope, this.cancel, this.parentUuid, this.user);
    }

}
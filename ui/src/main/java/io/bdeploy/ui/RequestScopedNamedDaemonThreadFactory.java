package io.bdeploy.ui;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.jersey.activity.JerseyBroadcastingActivityReporter;
import jakarta.inject.Provider;

/**
 * A {@link ThreadFactory} which creates threads which:
 * <ul>
 * <li>Has a proper given name.
 * <li>Is a daemon thread.
 * <li>Detaches BHive transactions from the parent thread.
 * <li>Runs inside of the current request's context.
 * </ul>
 */
public class RequestScopedNamedDaemonThreadFactory extends NamedDaemonThreadFactory {

    private final Provider<RequestScope> reqScope;
    private final BHiveTransactions tx;
    private final JerseyBroadcastingActivityReporter reporter;

    public RequestScopedNamedDaemonThreadFactory(Provider<RequestScope> req, BHiveTransactions tx,
            JerseyBroadcastingActivityReporter reporter, String name) {
        this(req, tx, reporter, () -> name);
    }

    public RequestScopedNamedDaemonThreadFactory(Provider<RequestScope> req, BHiveTransactions tx,
            JerseyBroadcastingActivityReporter reporter, Supplier<String> name) {
        super(name);

        this.reqScope = req;
        this.tx = tx;
        this.reporter = reporter;
    }

    @Override
    public Thread newThread(Runnable r) {
        RequestContext scope = reqScope.get().referenceCurrent();
        return super.newThread(() -> {
            tx.detachThread();

            reqScope.get().runInScope(scope, () -> {
                // must branch *inside* the request scope to be able to copy data over.
                try (NoThrowAutoCloseable c = reporter.branchThread()) {
                    r.run();
                }
            });
        });
    }

}

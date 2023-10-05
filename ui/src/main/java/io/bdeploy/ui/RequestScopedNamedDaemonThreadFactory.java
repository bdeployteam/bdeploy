package io.bdeploy.ui;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
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
    private final JerseyScopeService scopeService;

    public RequestScopedNamedDaemonThreadFactory(Provider<RequestScope> req, BHiveTransactions tx,
            JerseyScopeService scopeService, String name) {
        this(req, tx, scopeService, () -> name);
    }

    public RequestScopedNamedDaemonThreadFactory(Provider<RequestScope> req, BHiveTransactions tx,
            JerseyScopeService scopeService, Supplier<String> name) {
        super(name);

        this.reqScope = req;
        this.scopeService = scopeService;
        this.tx = tx;
    }

    @Override
    public Thread newThread(Runnable r) {
        RequestContext scope = reqScope.get().referenceCurrent();
        ObjectScope objscope = scopeService.getObjectScope();
        return super.newThread(() -> {
            if (tx != null) {
                tx.detachThread();
            }

            scopeService.setScope(objscope);
            reqScope.get().runInScope(scope, r::run);
        });
    }

}

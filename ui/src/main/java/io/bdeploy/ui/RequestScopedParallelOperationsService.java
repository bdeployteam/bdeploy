package io.bdeploy.ui;

import java.util.Collection;

import org.glassfish.jersey.process.internal.RequestScope;
import org.jvnet.hk2.annotations.Service;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.jersey.JerseyScopeService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@Service
public class RequestScopedParallelOperationsService {

    @Inject
    private Provider<RequestScope> reqScope;

    @Inject
    private JerseyScopeService jss;

    public void runAndAwaitAll(String id, Collection<Runnable> runnables, BHiveTransactions tx) {
        RequestScopedParallelOperations.runAndAwaitAll(id, runnables, reqScope, tx, jss);
    }

}

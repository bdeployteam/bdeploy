package io.bdeploy.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.glassfish.jersey.process.internal.RequestScope;

import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.jersey.JerseyScopeService;
import jakarta.inject.Provider;

public class RequestScopedParallelOperations {

    private static final int MAX_OPS = 4;

    public static void runAndAwaitAll(String id, Collection<Runnable> actions, Provider<RequestScope> scope, BHiveTransactions tx,
            JerseyScopeService scopeService) {
        // use the id plus a number for each new thread.
        AtomicLong threadNum = new AtomicLong(0);
        Supplier<String> threadId = () -> id + "-" + threadNum.incrementAndGet();

        // create a pool with a fixed size, which is capable of inheriting the current request scope.
        try (ExecutorService pool = Executors.newFixedThreadPool(MAX_OPS,
                new RequestScopedNamedDaemonThreadFactory(scope, tx, scopeService, threadId))) {

            // submit all tasks and map to their result.
            List<Future<?>> tasks = new ArrayList<>();
            tasks.addAll(actions.stream().map(pool::submit).toList());

            // wait for all tasks and shutdown the pool.
            FutureHelper.awaitAll(tasks);
        }
    }

}

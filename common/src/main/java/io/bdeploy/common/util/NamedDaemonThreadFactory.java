package io.bdeploy.common.util;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * A {@link ThreadFactory} which provides better names to threads.
 */
public class NamedDaemonThreadFactory implements ThreadFactory {

    private final Supplier<String> nameSupplier;

    public NamedDaemonThreadFactory(String name) {
        this(() -> name);
    }

    public NamedDaemonThreadFactory(Supplier<String> nameSupplier) {
        this.nameSupplier = nameSupplier;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName(nameSupplier.get());
        return t;
    }

}

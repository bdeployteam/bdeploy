package io.bdeploy.jersey.monitoring;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.HttpServerMonitoringConfig;
import org.glassfish.grizzly.http.server.HttpServerProbe;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;

public class JerseyServerMonitor {

    private HttpServer server;

    private final LongAdder conAccepted = new LongAdder();
    private final LongAdder conClosed = new LongAdder();
    private final LongAdder conConnected = new LongAdder();
    private final LongAdder conErrored = new LongAdder();

    private final LongAdder conBytesRead = new LongAdder();
    private final LongAdder conBytesWritten = new LongAdder();

    private final AtomicLong poolCoreSize = new AtomicLong();
    private final AtomicLong poolMaxSize = new AtomicLong();
    private final AtomicLong poolCurrentSize = new AtomicLong();
    private final LongAdder poolExceeded = new LongAdder();
    private final LongAdder poolTasksQueued = new LongAdder();
    private final LongAdder poolTasksCancelled = new LongAdder();
    private final LongAdder poolTasksFinished = new LongAdder();

    private final LongAdder reqReceived = new LongAdder();
    private final LongAdder reqCompleted = new LongAdder();
    private final LongAdder reqTimedOut = new LongAdder();
    private final LongAdder reqCancelled = new LongAdder();

    public void setServer(HttpServer server) {
        this.server = server;
        registerProbes();
    }

    public JerseyServerMonitoringSnapshot getSnapshot() {
        JerseyServerMonitoringSnapshot snapshot = new JerseyServerMonitoringSnapshot();

        snapshot.snapshotTime = System.currentTimeMillis();

        snapshot.conAccepted = conAccepted.longValue();
        snapshot.conClosed = conClosed.longValue();
        snapshot.conConnected = conConnected.longValue();
        snapshot.conErrored = conErrored.longValue();
        snapshot.conBytesRead = conBytesRead.longValue();
        snapshot.conBytesWritten = conBytesWritten.longValue();

        snapshot.poolCoreSize = poolCoreSize.longValue();
        snapshot.poolMaxSize = poolMaxSize.longValue();
        snapshot.poolCurrentSize = poolCurrentSize.longValue();
        snapshot.poolExceeded = poolExceeded.longValue();
        snapshot.poolTasksQueued = poolTasksQueued.longValue();
        snapshot.poolTasksCancelled = poolTasksCancelled.longValue();
        snapshot.poolTasksFinished = poolTasksFinished.longValue();

        snapshot.reqReceived = reqReceived.longValue();
        snapshot.reqCompleted = reqCompleted.longValue();
        snapshot.reqTimedOut = reqTimedOut.longValue();
        snapshot.reqCancelled = reqCancelled.longValue();

        snapshot.vmThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        snapshot.vmCpus = Runtime.getRuntime().availableProcessors();
        snapshot.vmMaxMem = Runtime.getRuntime().maxMemory();
        snapshot.vmFreeMem = Runtime.getRuntime().freeMemory();
        snapshot.vmTotalMem = Runtime.getRuntime().totalMemory();

        return snapshot;
    }

    @SuppressWarnings("rawtypes")
    private void registerProbes() {
        HttpServerMonitoringConfig monitoringConfig = server.getServerConfiguration().getMonitoringConfig();
        monitoringConfig.getConnectionConfig().addProbes(new ConnectionProbe.Adapter() {

            @Override
            public void onAcceptEvent(Connection serverConnection, Connection clientConnection) {
                conAccepted.increment();
            }

            @Override
            public void onCloseEvent(Connection connection) {
                conClosed.increment();
            }

            @Override
            public void onConnectEvent(Connection connection) {
                conConnected.increment();
            }

            @Override
            public void onErrorEvent(Connection connection, Throwable error) {
                conErrored.increment();
            }

            @Override
            public void onReadEvent(Connection connection, Buffer data, int size) {
                conBytesRead.add(size);
            }

            @Override
            public void onWriteEvent(Connection connection, Buffer data, long size) {
                conBytesWritten.add(size);
            }
        });

        // there is only a single thread pool, so no need to differentiate.
        monitoringConfig.getThreadPoolConfig().addProbes(new ThreadPoolProbe.Adapter() {

            @Override
            public void onThreadPoolStartEvent(AbstractThreadPool threadPool) {
                ThreadPoolConfig config = threadPool.getConfig();
                poolCoreSize.set(config.getCorePoolSize());
                poolMaxSize.set(config.getMaxPoolSize());
            }

            @Override
            public void onMaxNumberOfThreadsEvent(AbstractThreadPool threadPool, int maxNumberOfThreads) {
                poolExceeded.increment();
            }

            @Override
            public void onTaskCancelEvent(AbstractThreadPool threadPool, Runnable task) {
                poolTasksCancelled.increment();
            }

            @Override
            public void onTaskCompleteEvent(AbstractThreadPool threadPool, Runnable task) {
                poolTasksFinished.increment();
            }

            @Override
            public void onTaskQueueEvent(AbstractThreadPool threadPool, Runnable task) {
                poolTasksQueued.increment();
            }

            @Override
            public void onTaskDequeueEvent(AbstractThreadPool threadPool, Runnable task) {
                poolTasksQueued.decrement();
            }

            @Override
            public void onThreadAllocateEvent(AbstractThreadPool threadPool, Thread thread) {
                poolCurrentSize.set(threadPool.getSize());
            }

            @Override
            public void onThreadReleaseEvent(AbstractThreadPool threadPool, Thread thread) {
                poolCurrentSize.set(threadPool.getSize());
            }

        });

        monitoringConfig.getWebServerConfig().addProbes(new HttpServerProbe.Adapter() {

            @Override
            public void onRequestReceiveEvent(HttpServerFilter filter, Connection connection, Request request) {
                reqReceived.increment();
            }

            @Override
            public void onRequestCancelEvent(HttpServerFilter filter, Connection connection, Request request) {
                reqCancelled.increment();
            }

            @Override
            public void onRequestTimeoutEvent(HttpServerFilter filter, Connection connection, Request request) {
                reqTimedOut.increment();
            }

            @Override
            public void onRequestCompleteEvent(HttpServerFilter filter, Connection connection, Response response) {
                reqCompleted.increment();
            }
        });
    }

}

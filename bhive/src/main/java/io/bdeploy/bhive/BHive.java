package io.bdeploy.bhive;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.bdeploy.bhive.audit.AuditParameterExtractor;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.ObjectManager;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.AuditRecord.Severity;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.audit.NullAuditor;
import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.util.ExceptionHelper;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.ZipHelper;

/**
 * A high level management layer for storage repositories.
 * <p>
 * Encapsulates {@link ObjectDatabase} and {@link ManifestDatabase}, provides
 * over arching functionality.
 */
public class BHive implements AutoCloseable, BHiveExecution {

    private static final Logger log = LoggerFactory.getLogger(BHive.class);

    private final URI uri;
    private final FileSystem zipFs;
    private final Path objTmp;
    private final Path markerTmp;
    private final BHiveTransactions transactions;
    private final ObjectDatabase objects;
    private final ManifestDatabase manifests;
    private final ActivityReporter reporter;
    private final Auditor auditor;
    private int parallelism = 4;
    private boolean auditSlowOps = true;

    private Predicate<String> lockContentValidator = null;
    private Supplier<String> lockContentSupplier = null;

    private static final LoadingCache<String, Object> syncCache = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES)
            .build(CacheLoader.from(k -> new Object()));

    /**
     * Creates a new hive instance. Supports ZIP and directory hives.
     * <p>
     * To connect to a remote hive instead, use
     * {@link RemoteBHive#forService(io.bdeploy.common.security.RemoteService, String, ActivityReporter)}
     */
    public BHive(URI uri, Auditor auditor, ActivityReporter reporter) {
        this.uri = uri;
        Path relRoot;
        if (ZipHelper.isZipUri(uri)) {
            try {
                if (!uri.getScheme().equals("jar")) {
                    uri = URI.create("jar:" + uri);
                }

                Map<String, Object> env = new TreeMap<>();
                env.put("create", "true");
                env.put("useTempFile", Boolean.TRUE);
                this.zipFs = FileSystems.newFileSystem(uri, env);
            } catch (IOException e) {
                throw new IllegalStateException("cannot open or create ZIP BHive " + uri, e);
            }
            relRoot = zipFs.getPath("/");
        } else {
            relRoot = Paths.get(uri);
            this.zipFs = null;
        }

        Path objRoot = relRoot.resolve("objects");
        try {
            objTmp = zipFs == null ? relRoot.resolve("tmp") : Files.createTempDirectory("objdb-");
            markerTmp = zipFs == null ? relRoot.resolve("markers") : objTmp.resolve("markers");

            PathHelper.mkdirs(markerTmp);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create temporary directory for zipped BHive", e);
        }

        this.auditor = auditor == null ? new NullAuditor() : auditor;
        this.transactions = new BHiveTransactions(this, markerTmp, reporter);
        this.objects = new ObjectDatabase(objRoot, objTmp, reporter, transactions);
        this.manifests = new ManifestDatabase(relRoot.resolve("manifests"));
        this.reporter = reporter;
    }

    public URI getUri() {
        return uri;
    }

    /**
     * TESTING only, disable unpredictable logs for slow operations depending on machine.
     */
    public void setDisableSlowAudit(boolean disable) {
        this.auditSlowOps = !disable;
    }

    /**
     * Set the amount of threads to use for parallel-capable file operations.
     */
    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    /**
     * Retrieve the auditor for testing.
     */
    public Auditor getAuditor() {
        return auditor;
    }

    public void addSpawnListener(ManifestSpawnListener listener) {
        manifests.addSpawnListener(listener);
    }

    public void removeSpawnListener(ManifestSpawnListener listener) {
        manifests.removeSpawnListener(listener);
    }

    /**
     * Sets the supplier that provides the content that is written to a lock file.
     */
    public void setLockContentSupplier(Supplier<String> lockContentSupplier) {
        this.lockContentSupplier = lockContentSupplier;
    }

    /**
     * Sets the predicate that is used to validate an existing lock file.
     */
    public void setLockContentValidator(Predicate<String> lockContentValidator) {
        this.lockContentValidator = lockContentValidator;
    }

    /** Get the supplier that provides lock file content */
    protected Supplier<String> getLockContentSupplier() {
        return this.lockContentSupplier;
    }

    /** Get the predicate that is used to validate an existing lock file. */
    protected Predicate<String> getLockContentValidator() {
        return this.lockContentValidator;
    }

    @Override
    public Object getSynchronizationObject(String name) {
        try {
            return syncCache.get(name);
        } catch (ExecutionException e) {
            log.warn("Cannot get synchronization object for {}: {}", name, e.toString());
            return new Object();
        }
    }

    /**
     * Execute the given {@link Operation} on this {@link BHive}.
     */
    @Override
    public <T> T execute(Operation<T> op) {
        try {
            op.initOperation(this);
            return doExecute(op, 0);
        } finally {
            op.closeOperation();
        }
    }

    /**
     * Executes the given operation and writes some metrics about the overal execution time.
     */
    private final <T> T doExecute(Operation<T> op, int attempt) {
        try (Timer.Context timer = Metrics.getMetric(MetricGroup.HIVE).timer(op.getClass().getSimpleName()).time()) {
            if (op.getClass().getAnnotation(ReadOnlyOperation.class) == null) {
                auditor.audit(AuditRecord.Builder.fromSystem().setWhat(op.getClass().getSimpleName())
                        .addParameters(new AuditParameterExtractor().extract(op)).build());
            }

            long start = System.currentTimeMillis();
            try {
                return op.call();
            } finally {
                long timing = System.currentTimeMillis() - start;
                if (timing > 250 && auditSlowOps) {
                    auditor.audit(AuditRecord.Builder.fromSystem().setWhat(op.getClass().getSimpleName())
                            .addParameters(new AuditParameterExtractor().extract(op)).setMessage("Long running: " + timing + "ms")
                            .build());
                }
            }
        } catch (Exception ex) {
            onOperationFailed(op, ex);
            if (attempt >= op.retryCount) {
                throw new IllegalStateException("Operation on hive " + op.hive.getUri() + " failed: " + ex.toString(), ex);
            }
            onOperationRetry(op, attempt, ex);
            return doExecute(op, ++attempt);
        }
    }

    /** Audits the retry of the operation and delays the next retry. */
    private <T> void onOperationRetry(Operation<T> op, int attempt, Exception ex) {
        String retryString = (attempt + 1) + " / " + op.retryCount;
        auditor.audit(AuditRecord.Builder.fromSystem().setWhat(op.getClass().getSimpleName()).setSeverity(Severity.NORMAL)
                .setMessage("Retrying operation due to previous failure. Attempt " + retryString).build());

        log.warn("Operation failed. Attempt {}", retryString, ex);
        try (Activity activity = reporter.start("Operation failed (" + retryString + "). Waiting before next retry...",
                attempt)) {
            for (int sleep = 0; sleep <= attempt; sleep++) {
                Threads.sleep(1000);
                activity.worked(1);
            }
        }
    }

    /** Audits the failed operation. */
    private <T> void onOperationFailed(Operation<T> op, Exception e) {
        auditor.audit(AuditRecord.Builder.fromSystem().setWhat(op.getClass().getSimpleName()).setSeverity(Severity.ERROR)
                .addParameters(new AuditParameterExtractor().extract(op))
                .setMessage(ExceptionHelper.mapExceptionCausesToReason(e)).build());
    }

    /**
     * @return the {@link BHiveTransactions} tracker.
     */
    @Override
    public BHiveTransactions getTransactions() {
        return transactions;
    }

    @Override
    public void close() {
        if (zipFs != null) {
            try {
                zipFs.close();
            } catch (IOException e) {
                log.warn("Cannot close ZIP FS: {}", uri, e);
            }
            PathHelper.deleteRecursiveRetry(objTmp);
        }
        manifests.close();
        auditor.close();
    }

    /**
     * Base class for all operations that need to access internals of the
     * {@link BHive} they are executed on.
     */
    public abstract static class Operation<T> implements Callable<T>, BHiveExecution {

        private BHive hive;
        private ObjectManager mgr;
        private ExecutorService fileOps;
        private static final AtomicInteger fileOpNum = new AtomicInteger(0);

        /** Counter how often an operation should be retried. 0 means no retries at all */
        private int retryCount = 0;

        /**
         * Used internally to associate the operation with the executing hive
         */
        void initOperation(BHive hive) {
            this.hive = hive;
            this.fileOps = Executors.newFixedThreadPool(hive.parallelism,
                    new NamedDaemonThreadFactory(() -> "File-OPS-" + fileOpNum.incrementAndGet()));
            this.mgr = new ObjectManager(hive.objects, hive.manifests, hive.reporter, fileOps);
        }

        /**
         * Dissociates the operation from the associated hive.
         */
        private final void closeOperation() {
            fileOps.shutdownNow();
            hive = null;
            mgr = null;
        }

        /**
         * @return the {@link ObjectManager} to use when operating on the underlying
         *         {@link ObjectDatabase}.
         */
        protected ObjectManager getObjectManager() {
            return mgr;
        }

        /**
         * @return the underlying {@link ManifestDatabase}
         */
        protected ManifestDatabase getManifestDatabase() {
            return hive.manifests;
        }

        /**
         * @return the root path for marker databases which contribute to protected objects.
         */
        protected Path getMarkerRoot() {
            return hive.markerTmp;
        }

        /**
         * @return the {@link ActivityReporter} to manage {@link Activity}s with.
         */
        protected ActivityReporter getActivityReporter() {
            return hive.reporter;
        }

        /**
         * @return the {@link Auditor} associated with the current {@link BHive}.
         */
        protected Auditor getAuditor() {
            return hive.auditor;
        }

        /**
         * Returns the validator to check is a given lock file is still valid.
         */
        protected Predicate<String> getLockContentValidator() {
            return hive.lockContentValidator;
        }

        /**
         * Returns the supplier that provide the content to be written to the lock file.
         */
        protected Supplier<String> getLockContentSupplier() {
            return hive.lockContentSupplier;
        }

        @Override
        public BHiveTransactions getTransactions() {
            return hive.getTransactions();
        }

        @Override
        public Object getSynchronizationObject(String name) {
            return hive.getSynchronizationObject(name);
        }

        /**
         * Submit a {@link Runnable} performing a file operation to the pool managing
         * those operations.
         *
         * @param op the operation to run
         * @return a {@link Future} which can awaited, see
         *         {@link FutureHelper#awaitAll(java.util.Collection)}.
         */
        protected Future<?> submitFileOperation(Runnable op) {
            return fileOps.submit(op::run);
        }

        /**
         * Execute another {@link Operation} on the {@link BHive} which is currently
         * associated with this {@link Operation}.
         */
        @Override
        public <X> X execute(BHive.Operation<X> other) {
            return hive.execute(other);
        }

        /**
         * Sets the number of times the operation should be retried in case of an exception. The default value is '0' which means
         * that the operation is not retried on failure. A retry count of '4' means that the operation is executed up to 5 times
         * before giving up (First run + 4 retries).
         * <p>
         * The default value is '0' which means an operation is not retried in case of an exception.
         * </p>
         *
         * @param retryCount the number of times to retry the operation
         */
        public Operation<T> setRetryCount(int retryCount) {
            RuntimeAssert.assertTrue(retryCount >= 0, "Counter must be >=0 but was " + retryCount);
            this.retryCount = retryCount;
            return this;
        }
    }

    /**
     * Base class for operations which require an open transaction, set up by the caller.
     */
    public abstract static class TransactedOperation<T> extends Operation<T> {

        @Override
        public final T call() throws Exception {
            if (!super.getTransactions().hasTransaction()) {
                throw new IllegalStateException("Operation requires active transaction: " + getClass().getSimpleName());
            }

            return callTransacted();
        }

        /**
         * Executes the operation. The current thread is guaranteed to be associated with a transaction.
         */
        protected abstract T callTransacted() throws Exception;

    }

}

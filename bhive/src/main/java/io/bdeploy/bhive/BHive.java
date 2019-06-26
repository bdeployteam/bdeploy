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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

import io.bdeploy.bhive.audit.AuditParameterExtractor;
import io.bdeploy.bhive.objects.ManifestDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.objects.ObjectManager;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.jersey.audit.Auditor;
import io.bdeploy.jersey.audit.NullAuditor;
import io.bdeploy.jersey.audit.RollingFileAuditor;

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
    private final ObjectDatabase objects;
    private final ManifestDatabase manifests;
    private final ActivityReporter reporter;
    private final Auditor auditor;
    private int parallelism = 4;

    /**
     * Creates a new hive instance. Supports ZIP and directory hives.
     * <p>
     * To connect to a remote hive instead, use
     * {@link RemoteBHive#forService(io.bdeploy.common.security.RemoteService, String, ActivityReporter)}
     */
    public BHive(URI uri, ActivityReporter reporter) {
        this.uri = uri;

        Path relRoot;
        if (uri.getScheme().equals("jar") || (uri.getScheme().equals("file") && uri.toString().toLowerCase().endsWith(".zip"))) {
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
            this.auditor = new NullAuditor();
        } else {
            relRoot = Paths.get(uri);
            this.zipFs = null;
            this.auditor = new RollingFileAuditor(relRoot.resolve("log"));
        }

        Path objRoot = relRoot.resolve("objects");
        try {
            objTmp = zipFs == null ? objRoot : Files.createTempDirectory("objdb-");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create temporary directory for zipped BHive", e);
        }
        this.objects = new ObjectDatabase(objRoot, objTmp, reporter);
        this.manifests = new ManifestDatabase(relRoot.resolve("manifests"));
        this.reporter = reporter;
    }

    public URI getUri() {
        return uri;
    }

    /**
     * Set the amount of threads to use for parallel-capable file operations.
     */
    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    /**
     * Execute the given {@link Operation} on this {@link BHive}.
     */
    @Override
    public <T> T execute(Operation<T> op) {
        try (Timer.Context timer = Metrics.getMetric(MetricGroup.HIVE).timer(op.getClass().getSimpleName()).time()) {
            op.initOperation(this);
            auditor.audit(AuditRecord.Builder.fromSystem().setWhat(op.getClass().getSimpleName())
                    .addParameters(new AuditParameterExtractor().extract(op)).build());
            return op.call();
        } catch (Exception e) {
            throw new IllegalStateException("operation on hive " + uri + " failed", e);
        } finally {
            op.closeOperation();
        }
    }

    @Override
    public void close() {
        if (zipFs != null) {
            try {
                zipFs.close();
            } catch (IOException e) {
                log.warn("Cannot close ZIP FS: {}", uri, e);
            }
            PathHelper.deleteRecursive(objTmp);
        }
        auditor.close();
    }

    /**
     * Base class for all operations that need to access internals of the
     * {@link BHive} they are executed on.
     */
    abstract public static class Operation<T> implements Callable<T>, BHiveExecution {

        private BHive hive;
        private ObjectManager mgr;
        private ExecutorService fileOps;
        private static final AtomicInteger fileOpNum = new AtomicInteger(0);

        /**
         * Used internally to associate the operation with the executing hive
         */
        private final void initOperation(BHive hive) {
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
         * @return the {@link ActivityReporter} to manage {@link Activity}s with.
         */
        protected ActivityReporter getActivityReporter() {
            return hive.reporter;
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
            return fileOps.submit(op);
        }

        /**
         * Execute another {@link Operation} on the {@link BHive} which is currently
         * associated with this {@link Operation}.
         */
        @Override
        public <X> X execute(BHive.Operation<X> other) {
            return hive.execute(other);
        }

    }

}

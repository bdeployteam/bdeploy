package io.bdeploy.bhive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.op.AwaitDirectoryLockOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;

/**
 * Keeps track of running operations in the JVM.
 * <p>
 * Uses MarkerDatabase to synchronize with other JVMs which might have operations running as well.
 */
public class BHiveTransactions {

    private static final Logger log = LoggerFactory.getLogger(BHiveTransactions.class);

    private final InheritableThreadLocal<Stack<String>> transactions = new InheritableThreadLocal<>();
    private final Map<String, MarkerDatabase> dbs = new ConcurrentHashMap<>();
    private final BHive hive;
    private final ActivityReporter reporter;
    private final Path markerRoot;

    public BHiveTransactions(BHive hive, Path markerRoot, ActivityReporter reporter) {
        this.hive = hive;
        this.markerRoot = markerRoot;
        this.reporter = reporter;
    }

    private Stack<String> getOrCreate() {
        Stack<String> result = transactions.get();
        if (result == null) {
            result = new Stack<>();
            transactions.set(result);
        }
        return result;
    }

    /**
     * @param object the object which should be considered "touched", i.e. inserted.
     */
    public void touchObject(ObjectId object) {
        Stack<String> all = transactions.get();
        String id = (all == null || all.isEmpty()) ? null : all.peek();
        if (id == null) {
            throw new IllegalStateException("No transaction active while inserting object.");
        }

        MarkerDatabase mdb = dbs.get(id);
        if (mdb == null) {
            throw new IllegalStateException("Transaction database missing for transaction " + id);
        }

        mdb.addMarker(object);
    }

    /**
     * @return whether the current thread has an associated transaction.
     */
    public boolean hasTransaction() {
        Stack<String> stack = transactions.get();
        return stack != null && !stack.isEmpty();
    }

    /**
     * Begins a new transaction on this thread.
     * <p>
     * Inserts on the {@link ObjectDatabase} of a {@link BHive} will use this transaction to keep track of objects inserted.
     *
     * @return a {@link Transaction} which will cleanup associated resources when closed.
     */
    public Transaction begin() {
        hive.execute(new AwaitDirectoryLockOperation().setDirectory(markerRoot));

        String uuid = UuidHelper.randomId();
        getOrCreate().push(uuid);
        dbs.put(uuid, new MarkerDatabase(markerRoot.resolve(uuid), reporter));

        if (log.isDebugEnabled()) {
            log.debug("Starting transaction {}", uuid, new RuntimeException("Starting Transaction"));
        }

        return () -> {
            hive.execute(new AwaitDirectoryLockOperation().setDirectory(markerRoot));

            Stack<String> stack = transactions.get();
            if (stack == null || stack.isEmpty()) {
                throw new IllegalStateException("No transaction has been started on this thread!");
            }

            String top = stack.peek();
            if (!top.equals(uuid)) {
                log.warn("Out-of-order transaction found: {}, expected: {}", top, uuid);
            }

            if (log.isDebugEnabled()) {
                log.debug("Ending transaction {}", uuid, new RuntimeException("Ending Transaction"));
            }

            stack.remove(uuid);
            dbs.remove(uuid);

            Path mdb = markerRoot.resolve(uuid);
            if (!Files.isDirectory(mdb)) {
                return; // nothing to clean.
            }

            PathHelper.deleteRecursive(mdb);
        };
    }

    /**
     * Represents a writing transaction in the BHive.
     */
    public interface Transaction extends AutoCloseable {

        @Override
        public void close();
    }

}

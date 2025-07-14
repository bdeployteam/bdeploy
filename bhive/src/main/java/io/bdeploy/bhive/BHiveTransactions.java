package io.bdeploy.bhive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.MarkerDatabase;
import io.bdeploy.bhive.objects.ObjectDatabase;
import io.bdeploy.bhive.op.DirectoryAwaitOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.Threads;
import io.bdeploy.common.util.UuidHelper;

/**
 * Keeps track of running operations in the JVM.
 * <p>
 * Uses MarkerDatabase to synchronize with other JVMs which might have operations running as well.
 */
public class BHiveTransactions {

    private static final Logger log = LoggerFactory.getLogger(BHiveTransactions.class);
    private static final String TX_PID_FILE = "tx.pid";

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

    /**
     * Transactions are inherited by default. If this is not desired, this method can be used
     * to explicitly detach the current Thread from its parent (and potential sibling threads).
     */
    public void detachThread() {
        transactions.set(new Stack<>());
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
        hive.execute(new DirectoryAwaitOperation().setDirectory(markerRoot));

        String txid = UuidHelper.randomId();
        getOrCreate().push(txid);
        Path mdbPath = markerRoot.resolve(txid);
        dbs.put(txid, new MarkerDatabase(mdbPath, reporter));

        if (hive.getLockContentSupplier() != null) {
            String txValidationContent = hive.getLockContentSupplier().get();
            try {
                Files.write(mdbPath.resolve(TX_PID_FILE), Collections.singletonList(txValidationContent));
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Cannot write transaction validation information", e);
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Starting transaction {}", txid, new RuntimeException("Starting Transaction"));
        }

        return () -> {
            hive.execute(new DirectoryAwaitOperation().setDirectory(markerRoot));

            Stack<String> stack = transactions.get();
            if (stack == null || stack.isEmpty()) {
                throw new IllegalStateException("No transaction has been started on this thread!");
            }

            String top = stack.peek();
            if (!top.equals(txid)) {
                log.warn("Out-of-order transaction found: {}, expected: {}", top, txid);
            }

            if (log.isTraceEnabled()) {
                log.trace("Ending transaction {}", txid, new RuntimeException("Ending Transaction"));
            }

            stack.remove(txid);
            dbs.remove(txid);

            Path mdb = mdbPath;
            if (!Files.isDirectory(mdb)) {
                return; // nothing to clean.
            }

            // any exception in here will simply disappear into nirvana, as we're calling
            // in here from a try-with-resources. In case no other exception happens, this
            // will simply be ignored - so at least log it.
            try {
                PathHelper.deleteRecursiveRetry(mdb);
            } catch (Exception e) {
                log.warn("Cannot remove transaction {}", txid, e);
            }
        };
    }

    /**
     * This method can be used to detect and clean stale transactions which may keep (potentially damaged) objects alive.
     *
     * @return the amount of stale transactions found (and removed).
     */
    public long cleanStaleTransactions() {
        LongAdder amount = new LongAdder();
        try (var dir = Files.list(markerRoot)) {
            dir.forEach(p -> {
                int retries = 0;
                do {
                    if (Files.isDirectory(p) && PathHelper.exists(p.resolve(TX_PID_FILE))) {
                        // TX PID exists, validate.
                        if (hive.getLockContentValidator() == null) {
                            return; // cannot validate if transaction is still valid - should *never* happen.
                        }

                        try {
                            List<String> lines = Files.readAllLines(p.resolve(TX_PID_FILE));
                            if (!lines.isEmpty() && !StringHelper.isNullOrEmpty(lines.get(0)) && !hive.getLockContentValidator()
                                    .test(lines.get(0))) {
                                log.warn("Stale transaction detected, removing {}", p.getFileName());
                                PathHelper.deleteRecursiveRetry(p);
                                amount.increment();
                            }
                        } catch (IOException e) {
                            log.warn("Problem determining whether transaction is stale: {}", p.getFileName(), e);
                        }
                    } else if (Files.isDirectory(p)) {
                        // TX PID does NOT exist, this is either a *very* old or completely outdated transaction, or
                        // there is a race with whoever wants to create the TX PID file.
                        if (retries < 300) {
                            retries++;
                            Threads.sleep(10);
                            continue; // try again.
                        }

                        log.warn("Stale transaction detected, removing {} after {} retries", p.getFileName(), retries);
                        PathHelper.deleteRecursiveRetry(p);
                        amount.increment();
                    }
                    break;
                } while (true);
            });
        } catch (IOException e) {
            log.warn("Cannot list potentially stale transaction databases", e);
        }

        return amount.sum();
    }

    /**
     * Represents a writing transaction in the BHive.
     */
    public interface Transaction extends AutoCloseable {

        @Override
        public void close();
    }

}

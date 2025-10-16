package io.bdeploy.interfaces.history;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryManager;

@ExtendWith(TestHive.class)
class ParallelHistoryTest {

    @Test
    void testParallelRecords(BHive hive) {
        // create a dummy manifest which serves as "parent" for the history
        Manifest.Key key1 = new Manifest.Key("test", "1");
        createDummyManifest(hive, key1);

        // create the history and storm it.
        MinionRuntimeHistoryManager mrhm = new MinionRuntimeHistoryManager(key1, hive);

        testStormingTx(hive, mrhm);
    }

    @Test
    void testParallelRecordsWithParentTx(BHive hive) {
        // create a dummy manifest which serves as "parent" for the history
        Manifest.Key key1 = new Manifest.Key("test", "1");
        createDummyManifest(hive, key1);

        // create the history and storm it.
        MinionRuntimeHistoryManager mrhm = new MinionRuntimeHistoryManager(key1, hive);

        // no need to do anything in the tx, just the presence unlocks a new code path.
        try (BHiveTransactions.Transaction parent = hive.getTransactions().begin()) {
            testStormingTx(hive, mrhm);
        }
    }

    private void testStormingTx(BHive hive, MinionRuntimeHistoryManager mrhm) {
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 50; ++i) {
                int id = i;
                // this one alone is just fine, as recordEvent is synchronized :D
                futures.add(executor.submit(() -> {
                    Thread.currentThread().setName("e" + id);
                    mrhm.recordEvent(id, id, ProcessState.RUNNING, "abc" + id, "test");
                }));

                // bringing in another thread manipulating the same hive... that can cause issues even when synchronized as well.
                futures.add(executor.submit(() -> {
                    Thread.currentThread().setName("r" + id);
                    createDummyManifest(hive, new Manifest.Key("interference", String.valueOf(id)));
                }));
            }

            assertDoesNotThrow(() -> {
                FutureHelper.awaitAll(futures);
            });
        }
    }

    private synchronized void createDummyManifest(BHive hive, Manifest.Key key1) {
        try (BHiveTransactions.Transaction t = hive.getTransactions().begin()) {
            ObjectId emptyTree = hive.execute(new InsertArtificialTreeOperation().setTree(new Tree.Builder()));
            hive.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key1).setRoot(emptyTree).build(hive)));
        }
    }

}

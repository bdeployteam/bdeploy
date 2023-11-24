package io.bdeploy.dcu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHive.Operation;
import io.bdeploy.common.TaskSynchronizer;
import io.bdeploy.bhive.TestHive;

@ExtendWith(TestHive.class)
class OperationSynchronizerTest {

    @Test
    void testSynchronizer(BHive hive) throws Exception {
        TaskSynchronizer sync = new TaskSynchronizer();

        // setup a single operation which is performed by multiple threads at once. It should only be performed once.
        // Once the operation is done though, another request would be valid.

        CounterOperation cop = new CounterOperation();

        ExecutorService es = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; ++i) {
            es.submit(new Runnable() {

                @Override
                public void run() {
                    sync.perform("a", () -> hive.execute(cop));
                }
            });
        }

        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, cop.getCounter());

        // perform once more, this should now work again
        sync.perform("a", () -> hive.execute(cop));
        assertEquals(2, cop.getCounter());
    }

    private static class CounterOperation extends Operation<Void> {

        private long counter = 0;

        @Override
        public Void call() throws Exception {
            Thread.sleep(200);
            counter++;
            return null;
        }

        public long getCounter() {
            return counter;
        }

    }

}

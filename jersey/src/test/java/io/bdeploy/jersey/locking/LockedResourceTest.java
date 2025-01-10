package io.bdeploy.jersey.locking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.Threads;
import io.bdeploy.jersey.TestServer;

class LockedResourceTest {

    @RegisterExtension
    private final TestServer srv = new TestServer(LockedResourceImpl.class);

    private static final Logger log = LoggerFactory.getLogger(LockedResourceTest.class);

    @Test
    void testBlockWrites(LockedResource rsrc) throws Exception {
        assertEquals("Hello", rsrc.getValue());

        // start 2 threads, 2nd one should block and not throw an exception
        ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            Future<?> unlocked1 = es.submit(() -> rsrc.setValueUnlocked("World"));
            Future<?> unlocked2 = es.submit(() -> rsrc.setValueUnlocked("Universe"));

            assertThrows(ExecutionException.class, () -> {
                // either of the two must throw, we don't know which one.
                try {
                    unlocked1.get();
                } finally {
                    unlocked2.get();
                }
            });

            es.submit(() -> {
                for (int i = 0; i < 7; ++i) {
                    log.info(rsrc.getValue());
                    Threads.sleep(100);
                }
            });

            Future<?> locked1 = es.submit(() -> rsrc.setValue("World"));
            Threads.sleep(100);
            Future<?> locked2 = es.submit(() -> rsrc.setValue("Universe"));

            locked1.get();
            locked2.get();

            assertEquals("Universe", rsrc.getValue());
        } finally {
            es.shutdownNow();
        }
    }

}

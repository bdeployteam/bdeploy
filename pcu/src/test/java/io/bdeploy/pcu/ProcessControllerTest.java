package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.pcu.util.ProcessHandles;

@ExtendWith(TempDirectory.class)
public class ProcessControllerTest {

    @Test
    public void testStartStop(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App1", false, "100");
        process.addStatusListener(listener);

        // Start and wait until the application is running
        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING);
        process.start();
        latch.await();
        assertEquals(process.getState(), ProcessState.RUNNING);

        latch = listener.expect(process, ProcessState.STOPPED);
        process.stop();
        latch.await();

        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testRecover(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App2", false, "100");
        process.addStatusListener(listener);

        // Start and wait for running
        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING);
        process.start();
        latch.await();

        // Overwrite instance and try to recover
        process.detach();
        process = TestFactory.create(tmp, "App1", false, "600");
        latch = listener.expect(process, ProcessState.RUNNING);
        process.addStatusListener(listener);
        process.recover();
        latch.await();

        // Process must still be running
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Terminate after recovering
        latch = listener.expect(process, ProcessState.STOPPED);
        process.stop();
        latch.await();
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testRecoverAfterCrash(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App3", true, "100");
        process.addStatusListener(listener);
        process.setRecoverDelays(Duration.ofSeconds(1));
        process.setStableThreshold(Duration.ofSeconds(1));

        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING);
        process.start();
        latch.await();
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Terminate process using the provided PID
        ProcessHandle handle = ProcessHandle.of(process.getStatus().processDetails.pid).get();
        ProcessHandles.destroy(handle);

        // Check state transitions from waiting to running
        latch = listener.expect(process, ProcessState.CRASH_BACK_OFF, ProcessState.RUNNING_UNSTABLE, ProcessState.RUNNING);
        latch.await();

        // Now shutdown the process again
        latch = listener.expect(process, ProcessState.STOPPED);
        process.stop();
        latch.await();
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testStopWhileInCrashBackOff(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App4", true, "1");
        process.addStatusListener(listener);

        // Increment duration so that we can test the stopping
        Duration recoverDelay = Duration.ofSeconds(10);
        process.setRecoverDelays(recoverDelay);
        process.setStableThreshold(Duration.ZERO);

        // Start and wait until it is running
        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING);
        process.start();
        latch.await();
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Wait until it crashes
        latch = listener.expect(process, ProcessState.CRASH_BACK_OFF);
        latch.await();

        // Check if properties are set
        ProcessStatusDto status = process.getStatus();
        assertEquals(1, status.retryCount);
        assertEquals(10, status.recoverDelay);

        // Recover task must be scheduled
        assertNotNull(process.getRecoverTask());

        // Now execute stop command
        latch = listener.expect(process, ProcessState.STOPPED);
        process.stop();
        latch.await();

        // Recover task must be canceled
        assertTrue(process.getRecoverTask() == null);
    }

    @Test
    public void testStartStopInParallel(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App5", true, "100");
        process.addStatusListener(listener);
        AtomicInteger failedCounter = new AtomicInteger(0);

        // Start in parallel
        final CountDownLatch startLock = new CountDownLatch(3);
        Thread t1 = new Thread(() -> {
            startLock.countDown();
            try {
                process.start();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        Thread t2 = new Thread(() -> {
            startLock.countDown();
            try {
                process.start();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING);
        startLock.countDown();
        latch.await();

        // Wait for both threads to finish
        t1.join();
        t2.join();

        // One thread must failed to start because the other one holds the lock
        assertEquals(1, failedCounter.get());
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Stop in parallel
        failedCounter.set(0);
        CountDownLatch stopLock = new CountDownLatch(3);
        t1 = new Thread(() -> {
            stopLock.countDown();
            try {
                process.stop();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        t2 = new Thread(() -> {
            stopLock.countDown();
            try {
                process.stop();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        latch = listener.expect(process, ProcessState.STOPPED);
        stopLock.countDown();
        latch.await();

        // One thread must failed to start because the other one holds the lock
        assertEquals(1, failedCounter.get());
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testCrashWithoutRecover(@TempDir Path tmp) throws Exception {
        CountDownListener listener = new CountDownListener();
        ProcessController process = TestFactory.create(tmp, "App6", true, "1");
        process.addStatusListener(listener);
        process.setRecoverDelays();

        // Start and wait until it terminates
        CountDownLatch latch = listener.expect(process, ProcessState.RUNNING, ProcessState.STOPPED_CRASHED);
        process.start();
        latch.await();

        // Check final state
        assertEquals(process.getState(), ProcessState.STOPPED_CRASHED);
    }

}

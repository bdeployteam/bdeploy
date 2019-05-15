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

    private final static Duration TIMEOUT = Duration.ofSeconds(60);

    @Test
    public void testStartStop(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App1", false, "100");
        process.addStatusListener(listener);

        // Start and wait until the application is running
        listener.expect(process, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.RUNNING);

        listener.expect(process, ProcessState.STOPPED);
        process.stop();
        listener.await(TIMEOUT);

        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testRecover(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App2", false, "100");
        process.addStatusListener(listener);

        // Start and wait for running
        listener.expect(process, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);
        process.detach();

        // Overwrite instance
        process = process.newInstance();
        process.addStatusListener(listener);

        // Try to recover
        listener.expect(process, ProcessState.RUNNING);
        process.recover();
        listener.await(TIMEOUT);

        // Process must still be running
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Terminate after recovering
        listener.expect(process, ProcessState.STOPPED);
        process.stop();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testRecoverAfterCrash(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App3", true, "100");
        process.addStatusListener(listener);
        process.setRecoverDelays(Duration.ofSeconds(1));
        process.setStableThreshold(Duration.ofSeconds(1));

        listener.expect(process, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Terminate process using the provided PID
        listener.expect(process, ProcessState.CRASHED_WAITING, ProcessState.RUNNING_UNSTABLE, ProcessState.RUNNING);
        ProcessHandle handle = ProcessHandle.of(process.getStatus().processDetails.pid).get();
        ProcessHandles.destroy(handle);

        // Wait for state transitions from waiting to running
        listener.await(TIMEOUT);

        // Now shutdown the process again
        listener.expect(process, ProcessState.STOPPED);
        process.stop();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testStopWhileInCrashBackOff(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App4", true, "1");
        process.addStatusListener(listener);

        // Increment duration so that we can test the stopping
        process.setRecoverDelays(Duration.ofSeconds(10));
        process.setStableThreshold(Duration.ZERO);

        // Start and wait until it is running
        listener.expect(process, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Wait until it crashes
        listener.expect(process, ProcessState.CRASHED_WAITING);
        listener.await(TIMEOUT);

        // Check if properties are set
        ProcessStatusDto status = process.getStatus();
        assertEquals(1, status.retryCount);
        assertEquals(10, status.recoverDelay);

        // Recover task must be scheduled
        assertNotNull(process.getRecoverTask());

        // Now execute stop command
        listener.expect(process, ProcessState.STOPPED);
        process.stop();
        listener.await(TIMEOUT);

        // Recover task must be canceled
        assertTrue(process.getRecoverTask() == null);
    }

    @Test
    public void testStartStopInParallel(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App5", true, "100");
        process.addStatusListener(listener);
        AtomicInteger failedCounter = new AtomicInteger(0);

        // Start in parallel
        final CountDownLatch startLock = new CountDownLatch(3);
        Thread t1 = new Thread(() -> {
            startLock.countDown();
            try {
                startLock.await();
                process.start();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        Thread t2 = new Thread(() -> {
            startLock.countDown();
            try {
                startLock.await();
                process.start();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        listener.expect(process, ProcessState.RUNNING);
        startLock.countDown();
        startLock.await();
        listener.await(TIMEOUT);

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
                stopLock.await();
                process.stop();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        t2 = new Thread(() -> {
            stopLock.countDown();
            try {
                stopLock.await();
                process.stop();
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        listener.expect(process, ProcessState.STOPPED);
        stopLock.countDown();
        stopLock.await();
        listener.await(TIMEOUT);

        // One thread must failed to start because the other one holds the lock
        assertEquals(1, failedCounter.get());
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testRecoverAttemptsExceeded(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App6", true, "1");
        process.addStatusListener(listener);
        process.setRecoverAttempts(1);

        // Start and wait until it permanently crashes
        listener.expect(process, ProcessState.CRASHED_PERMANENTLY);
        process.start();
        listener.await(TIMEOUT);

        // Check final state
        ProcessStatusDto status = process.getStatus();
        assertEquals(ProcessState.CRASHED_PERMANENTLY, status.processState);
        assertEquals(1, status.maxRetryCount);
        assertEquals(1, status.retryCount);
        assertEquals(10, status.recoverDelay);
    }

    @Test
    public void testStopWithoutRecover(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App7", false, "1");
        process.addStatusListener(listener);

        // Start and wait until it terminates
        listener.expect(process, ProcessState.RUNNING, ProcessState.STOPPED);
        process.start();
        listener.await(TIMEOUT);

        // Check final state
        assertEquals(process.getState(), ProcessState.STOPPED);
    }

    @Test
    public void testStartWhileInCrashBackOff(@TempDir Path tmp) throws Exception {
        StateListener listener = new StateListener();
        ProcessController process = TestFactory.create(tmp, "App8", true, "300");
        process.addStatusListener(listener);

        // Increment duration so that we can test the starting
        process.setRecoverDelays(Duration.ofSeconds(10));
        process.setStableThreshold(Duration.ZERO);

        // Start and wait until it is running
        listener.expect(process, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);
        assertEquals(process.getState(), ProcessState.RUNNING);

        // Terminate process using the provided PID
        listener.expect(process, ProcessState.CRASHED_WAITING);
        ProcessHandle handle = ProcessHandle.of(process.getStatus().processDetails.pid).get();
        ProcessHandles.destroy(handle);
        listener.await(TIMEOUT);

        // Check if properties are set and that the recover task is scheduled
        ProcessStatusDto status = process.getStatus();
        assertEquals(1, status.retryCount);
        assertEquals(10, status.recoverDelay);
        assertNotNull(process.getRecoverTask());

        // Now execute start command
        listener.expect(process, ProcessState.RUNNING_UNSTABLE, ProcessState.RUNNING);
        process.start();
        listener.await(TIMEOUT);

        // Recover task must be canceled and counter must be reset due to the manual start
        assertTrue(process.getRecoverTask() == null);
        status = process.getStatus();
        assertEquals(0, status.retryCount);

        // Now execute stop command
        listener.expect(process, ProcessState.STOPPED);
        process.stop();
        listener.await(TIMEOUT);
    }

}

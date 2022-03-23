package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;

class ProcessControllerTest {

    private final static Duration TIMEOUT = Duration.ofSeconds(60);

    @Test
    void testStartStop(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App1", false, "60");
        StateListener listener = StateListener.createFor(process);

        // Start and wait until the application is running
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.RUNNING, process.getState());

        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);

        assertEquals(ProcessState.STOPPED, process.getState());
        assertNotEquals(0, process.getStatus().exitCode);
    }

    @Test
    void testRecover(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App2", false, "100");
        StateListener listener = StateListener.createFor(process);

        // Start and wait for running
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        process.detach();

        // Overwrite instance
        process = process.newInstance();
        listener = StateListener.createFor(process);

        // Try to recover
        listener.expect(ProcessState.RUNNING);
        process.recover();
        listener.await(TIMEOUT);

        // Process must still be running
        assertEquals(ProcessState.RUNNING, process.getState());

        // Terminate after recovering
        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.STOPPED, process.getState());
    }

    @Test
    void testRecoverAfterCrash(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App3", true, "100");
        process.setRecoverDelays(Duration.ofSeconds(1));
        process.setStableThreshold(Duration.ofSeconds(1));

        StateListener listener = StateListener.createFor(process);
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.RUNNING, process.getState());

        // Terminate process using the provided PID
        listener.expect(ProcessState.CRASHED_WAITING, ProcessState.RUNNING_UNSTABLE, ProcessState.RUNNING);
        ProcessHandle handle = ProcessHandle.of(process.getDetails().handle.pid).get();
        ProcessHandles.destroy(handle);

        // Wait for state transitions from waiting to running
        listener.await(TIMEOUT);

        // Now shutdown the process again
        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.STOPPED, process.getState());
    }

    @Test
    void testStopWhileInCrashBackOff(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App4", true, "1");
        StateListener listener = StateListener.createFor(process);

        // Increment duration so that we can test the stopping
        process.setRecoverDelays(Duration.ofSeconds(10));
        process.setStableThreshold(Duration.ZERO);

        // Start and wait until it is running
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.RUNNING, process.getState());

        // Wait until it crashes
        listener.expect(ProcessState.CRASHED_WAITING);
        listener.await(TIMEOUT);

        // Recover task must be scheduled
        assertNotNull(process.getRecoverTask());

        // Now execute stop command
        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);

        // Recover task must be canceled
        assertTrue(process.getRecoverTask() == null);
    }

    @Test
    void testStartStopInParallel(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App5", true, "100");
        StateListener listener = StateListener.createFor(process);
        AtomicInteger failedCounter = new AtomicInteger(0);

        // Start in parallel
        final CountDownLatch startLock = new CountDownLatch(3);
        Thread t1 = new Thread(() -> {
            startLock.countDown();
            try {
                startLock.await();
                process.start(null);
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        Thread t2 = new Thread(() -> {
            startLock.countDown();
            try {
                startLock.await();
                process.start(null);
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        listener.expect(ProcessState.RUNNING);
        startLock.countDown();
        startLock.await();
        listener.await(TIMEOUT);

        // Wait for both threads to finish
        t1.join();
        t2.join();

        // No thread may fail. One waits for the other and then simply does nothing
        assertEquals(0, failedCounter.get());
        assertEquals(ProcessState.RUNNING, process.getState());

        // Stop in parallel
        failedCounter.set(0);
        CountDownLatch stopLock = new CountDownLatch(3);
        t1 = new Thread(() -> {
            stopLock.countDown();
            try {
                stopLock.await();
                process.stop(null);
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t1.start();
        t2 = new Thread(() -> {
            stopLock.countDown();
            try {
                stopLock.await();
                process.stop(null);
            } catch (Exception ex) {
                failedCounter.addAndGet(1);
            }
        });
        t2.start();

        // Release lock so that both are running
        listener.expect(ProcessState.STOPPED);
        stopLock.countDown();
        stopLock.await();
        listener.await(TIMEOUT);

        // No thread may fail. One waits for the other and then simply does nothing
        assertEquals(0, failedCounter.get());
        assertEquals(ProcessState.STOPPED, process.getState());
    }

    @Test
    void testRecoverAttemptsExceeded(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App6", true, "1");
        process.setRecoverAttempts(1);

        // Start and wait until it permanently crashes
        StateListener listener = StateListener.createFor(process);
        listener.expect(ProcessState.CRASHED_PERMANENTLY);
        process.start(null);
        listener.await(TIMEOUT);

        // Check final state
        ProcessStatusDto status = process.getStatus();
        assertEquals(ProcessState.CRASHED_PERMANENTLY, status.processState);

        ProcessDetailDto details = process.getDetails();
        assertEquals(1, details.maxRetryCount);
        assertEquals(1, details.retryCount);
        assertEquals(10, details.recoverDelay);
    }

    @Test
    void testStopWithoutRecover(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App7", false, "1");
        StateListener listener = StateListener.createFor(process);

        // Start and wait until it terminates - might not even reach RUNNING state.
        listener.expect(ProcessState.RUNNING_NOT_STARTED, ProcessState.STOPPED);
        process.start(null);
        listener.await(TIMEOUT);

        // Check final state
        assertEquals(ProcessState.STOPPED, process.getState());
        assertEquals(0, process.getStatus().exitCode);
    }

    @Test
    void testStartWhileInCrashBackOff(@TempDir Path tmp) throws Exception {
        ProcessController process = TestFactory.create(tmp, "App8", true, "300");
        StateListener listener = StateListener.createFor(process);

        // Increment duration so that we can test the starting
        process.setRecoverDelays(Duration.ofSeconds(10));
        process.setStableThreshold(Duration.ZERO);

        // Start and wait until it is running
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.RUNNING, process.getState());

        // Terminate process using the provided PID
        listener.expect(ProcessState.CRASHED_WAITING);
        ProcessHandle handle = ProcessHandle.of(process.getDetails().handle.pid).get();
        ProcessHandles.destroy(handle);
        listener.await(TIMEOUT);

        assertNotNull(process.getRecoverTask());

        // Now execute start command
        listener.expect(ProcessState.RUNNING_UNSTABLE, ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);

        // Recover task must be canceled and counter must be reset due to the manual start
        assertTrue(process.getRecoverTask() == null);
        ProcessDetailDto details = process.getDetails();
        assertEquals(0, details.retryCount);

        // Now execute stop command
        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);
    }

    @Test
    void testVariableReplacement(@TempDir Path tmp) throws Exception {
        Map<String, String> vars = Collections.singletonMap("SLEEP_TIMEOUT", "100");

        ProcessController process = TestFactory.create(tmp, "App9", false, "{{SLEEP_TIMEOUT}}");
        process.setVariableResolver(vars::get);
        StateListener listener = StateListener.createFor(process);

        // Start and wait until the application is running
        listener.expect(ProcessState.RUNNING);
        process.start(null);
        listener.await(TIMEOUT);
        assertEquals(ProcessState.RUNNING, process.getState());

        listener.expect(ProcessState.STOPPED);
        process.stop(null);
        listener.await(TIMEOUT);

        assertEquals(ProcessState.STOPPED, process.getState());
    }

}

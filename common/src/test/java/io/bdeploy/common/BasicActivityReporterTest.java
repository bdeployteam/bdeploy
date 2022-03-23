package io.bdeploy.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.bdeploy.common.ActivityReporter.Activity;

class BasicActivityReporterTest {

    private static ActivityReporter.Stream stream = new ActivityReporter.Stream(System.out);

    @BeforeAll
    static void initStream() {
        stream.beginReporting();
    }

    @AfterAll
    static void deinitStream() {
        stream.stopReporting();
    }

    static Stream<ActivityReporter> basicTest() {
        return Stream.of(stream, new ActivityReporter.Null());
    }

    @ParameterizedTest
    @MethodSource
    void basicTest(ActivityReporter reporter) throws Exception {
        try (Activity indeterminate = reporter.start("Indeterminate")) {
            indeterminate.worked(1);

            try (Activity nested = reporter.start("Determinate", 2)) {
                nested.worked(1);
                nested.workAndCancelIfRequested(1);
            }
        }

        AtomicLong v = new AtomicLong(0);
        try (Activity supplied = reporter.start("Fully Supplied", () -> 2l, () -> v.get())) {
            v.set(2);
        }

        // if we reach this, we're ok :)
    }

}

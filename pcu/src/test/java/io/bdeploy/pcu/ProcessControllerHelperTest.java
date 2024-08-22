package io.bdeploy.pcu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProcessControllerHelperTest {

    @Test
    void testFormatDuration() {
        // Test zero duration

        assertEquals("0 milliseconds", ProcessControllerHelper.formatDuration(Duration.ZERO));

        // Test without gaps

        Duration _1d = Duration.ofDays(1);
        Duration _1d1h = _1d.plus(Duration.ofHours(1));
        Duration _1d1h1min = _1d1h.plus(Duration.ofMinutes(1));
        Duration _1d1h1min1sec = _1d1h1min.plus(Duration.ofSeconds(1));
        Duration _1d1h1min1sec1milli = _1d1h1min1sec.plus(Duration.ofMillis(1));
        Duration _8d = Duration.ofDays(8);
        Duration _8d5h = _8d.plus(Duration.ofHours(5));
        Duration _8d5h2min = _8d5h.plus(Duration.ofMinutes(2));
        Duration _8d5h2min6sec = _8d5h2min.plus(Duration.ofSeconds(6));
        Duration _8d5h2min6sec3milli = _8d5h2min6sec.plus(Duration.ofMillis(3));

        assertWithNegation("1 day", _1d);
        assertWithNegation("1 day 1 hour", _1d1h);
        assertWithNegation("1 day 1 hour 1 minute", _1d1h1min);
        assertWithNegation("1 day 1 hour 1 minute", _1d1h1min1sec);
        assertWithNegation("1 day 1 hour 1 minute", _1d1h1min1sec1milli);
        assertWithNegation("8 days", _8d);
        assertWithNegation("8 days 5 hours", _8d5h);
        assertWithNegation("8 days 5 hours 2 minutes", _8d5h2min);
        assertWithNegation("8 days 5 hours 2 minutes", _8d5h2min6sec);
        assertWithNegation("8 days 5 hours 2 minutes", _8d5h2min6sec3milli);

        Duration _1h = Duration.ofHours(1);
        Duration _1h1min = _1h.plus(Duration.ofMinutes(1));
        Duration _1h1min1sec = _1h1min.plus(Duration.ofSeconds(1));
        Duration _1h1min1sec1milli = _1h1min1sec.plus(Duration.ofMillis(1));
        Duration _5h = Duration.ofHours(5);
        Duration _5h2min = _5h.plus(Duration.ofMinutes(2));
        Duration _5h2min6sec = _5h2min.plus(Duration.ofSeconds(6));
        Duration _5h2min6sec3milli = _5h2min6sec.plus(Duration.ofMillis(3));

        assertWithNegation("1 hour", _1h);
        assertWithNegation("1 hour 1 minute", _1h1min);
        assertWithNegation("1 hour 1 minute", _1h1min1sec);
        assertWithNegation("1 hour 1 minute", _1h1min1sec1milli);
        assertWithNegation("5 hours", _5h);
        assertWithNegation("5 hours 2 minutes", _5h2min);
        assertWithNegation("5 hours 2 minutes", _5h2min6sec);
        assertWithNegation("5 hours 2 minutes", _5h2min6sec3milli);

        Duration _1min = Duration.ofMinutes(1);
        Duration _1min1sec = _1min.plus(Duration.ofSeconds(1));
        Duration _1min1sec1milli = _1min1sec.plus(Duration.ofMillis(1));
        Duration _2min = Duration.ofMinutes(2);
        Duration _2min6sec = _2min.plus(Duration.ofSeconds(6));
        Duration _2min6sec3milli = _2min6sec.plus(Duration.ofMillis(3));

        assertWithNegation("1 minute", _1min);
        assertWithNegation("1 minute 1 second", _1min1sec);
        assertWithNegation("1 minute 1 second", _1min1sec1milli);
        assertWithNegation("2 minutes", _2min);
        assertWithNegation("2 minutes 6 seconds", _2min6sec);
        assertWithNegation("2 minutes 6 seconds", _2min6sec3milli);

        Duration _1sec = Duration.ofSeconds(1);
        Duration _1sec1milli = _1sec.plus(Duration.ofMillis(1));
        Duration _6sec = Duration.ofSeconds(6);
        Duration _6sec3milli = _6sec.plus(Duration.ofMillis(3));

        assertWithNegation("1 second", _1sec);
        assertWithNegation("1 second 1 millisecond", _1sec1milli);
        assertWithNegation("6 seconds", _6sec);
        assertWithNegation("6 seconds 3 milliseconds", _6sec3milli);

        Duration _1milli = Duration.ofMillis(1);
        Duration _3milli = Duration.ofMillis(3);

        assertWithNegation("1 millisecond", _1milli);
        assertWithNegation("3 milliseconds", _3milli);

        // Test with gaps

        Duration _1d1min = Duration.ofMinutes(1441);
        Duration _5d3min = Duration.ofMinutes(7203);
        assertWithNegation("1 day 1 minute", _1d1min);
        assertWithNegation("5 days 3 minutes", _5d3min);

        Duration _1h1sec = Duration.ofSeconds(3601);
        Duration _5h3sec = Duration.ofSeconds(18003);
        assertWithNegation("1 hour", _1h1sec);
        assertWithNegation("5 hours", _5h3sec);
    }

    private static void assertWithNegation(String expectedPositive, Duration d) {
        assertEquals(expectedPositive, ProcessControllerHelper.formatDuration(d));
        assertEquals('-' + expectedPositive, ProcessControllerHelper.formatDuration(d.multipliedBy(-1)));
    }
}

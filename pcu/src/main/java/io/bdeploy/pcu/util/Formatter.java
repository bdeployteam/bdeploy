package io.bdeploy.pcu.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;

/**
 * Helper to format duration and times.
 */
public class Formatter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Formats the given instant into a human readable string.
     *
     * <pre>
     *      28.03.2019 15:36:45
     * </pre>
     *
     * @param value
     *            the value to format
     * @return the formatted value
     */
    public static String formatInstant(Instant value) {
        return DATE_TIME_FORMATTER.format(value);
    }

    /**
     * Formats the given duration into a human readable string.
     *
     * <pre>
    *       3 days 4 hours 1 minute
    *       5 minutes 2 seconds
    *       5 seconds 360 milliseconds
     * </pre>
     *
     * @param value
     *            the value to format
     * @return the formatted value
     */
    public static String formatDuration(Duration value) {
        List<String> parts = new ArrayList<>();
        long days = value.toDaysPart();
        if (days > 0) {
            parts.add(days + (days == 1 ? " day" : " days"));
        }
        long hours = value.toHoursPart();
        if (hours > 0) {
            parts.add(hours + (hours == 1 ? " hour" : " hours"));
        }
        long minutes = value.toMinutesPart();
        if (minutes > 0) {
            parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        }

        // Skip seconds if we have days, hours
        if (days > 0 || hours > 0) {
            return Joiner.on(" ").join(parts);
        }
        long seconds = value.toSecondsPart();
        if (seconds > 0) {
            parts.add(seconds + (seconds == 1 ? " second" : " seconds"));
        }

        // Skip milliseconds if we have minutes
        if (minutes > 0) {
            return Joiner.on(" ").join(parts);
        }
        long millis = value.toMillisPart();
        if (millis > 0 || parts.isEmpty()) {
            parts.add(millis + (millis == 1 ? " millisecond" : " milliseconds"));
        }
        return Joiner.on(" ").join(parts);
    }

}

package io.bdeploy.pcu.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper to format duration and times.
 */
public class Formatter {

    private Formatter() {
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
        if (days == 1) {
            parts.add(days + " day");
        } else if (days > 1) {
            parts.add(days + " days");
        }
        long hours = value.toHoursPart();
        if (hours == 1) {
            parts.add(hours + " hour");
        } else if (hours > 1) {
            parts.add(hours + " hours");
        }
        long minutes = value.toMinutesPart();
        if (minutes == 1) {
            parts.add(minutes + " minute");
        } else if (minutes > 1) {
            parts.add(minutes + " minutes");
        }

        // Skip seconds if we have days, hours
        if (days > 0 || hours > 0) {
            return String.join(" ", parts);
        }
        long seconds = value.toSecondsPart();
        if (seconds == 1) {
            parts.add(seconds + " second");
        } else if (seconds > 1) {
            parts.add(seconds + " seconds");
        }

        // Skip milliseconds if we have minutes
        if (minutes > 0) {
            return String.join(" ", parts);
        }
        long millis = value.toMillisPart();
        if (millis == 1) {
            parts.add(millis + " millisecond");
        } else if (millis > 1 || parts.isEmpty()) {
            parts.add(millis + " milliseconds");
        }
        return String.join(" ", parts);
    }

}

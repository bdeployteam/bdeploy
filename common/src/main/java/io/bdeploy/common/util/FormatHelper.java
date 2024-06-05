package io.bdeploy.common.util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

/**
 * Contains static helpers to format various data types into a human readable format.
 */
public class FormatHelper {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.#");

    private static final DecimalFormat TRANSFER_FORMAT = new DecimalFormat("#0.0");

    private static final String[] SIZE_UNITS = new String[] { "B", "kB", "MB", "GB", "TB" };

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    private FormatHelper() {
    }

    /**
     * Formats the given duration into a human readable format
     */
    public static String formatDuration(long timeInMillis) {
        return new SimpleDateFormat("mm 'min' ss 'sec' SSS 'ms'").format(new Date(timeInMillis));
    }

    /**
     * Returns a human readable string of the given time. Only the most relevant unit is displayed.
     *
     * <pre>
     * Sample output:  4 seconds
     *                 15 minutes
     *                 1 hour
     * </pre>
     *
     * @param duration
     *            The duration in milliseconds
     * @return The string representation.
     */
    public static String formatRemainingTime(long duration) {
        Duration d = Duration.ofMillis(duration);

        long hours = d.toHours();
        if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        }

        long minutes = d.toMinutes();
        if (minutes > 0) {
            return minutes + (minutes == 1 ? " min" : " mins");
        }

        long seconds = d.toSeconds();
        if (seconds > 0) {
            return seconds + (seconds == 1 ? " sec" : " secs");
        }
        return "0 secs";
    }

    /**
     * Calculates and formats the transfer rate into a human readable string
     *
     * @param bytes the number of bytes that have been transfered
     * @param timeInMs the time in milliseconds that the transfer took
     */
    public static String formatTransferRate(long bytes, long timeInMs) {
        if (bytes <= 0 || timeInMs < 1000) {
            return "N/A";
        }
        double bps = (bytes * 8) / (timeInMs / 1000.0);
        double kBs = bps / 8000.0;
        if (kBs > 1000) {
            return TRANSFER_FORMAT.format(kBs / 1000.0) + " MB/s";
        }
        return TRANSFER_FORMAT.format(kBs) + " KB/s";
    }

    /**
     * Formats the given date into a human readable string
     */
    public static String format(Date date) {
        return FORMATTER.format(date.toInstant());
    }

    /**
     * Formats the given date into a human readable string
     */
    public static String format(TemporalAccessor temporal) {
        return FORMATTER.format(temporal);
    }

    /**
     * Formats the given date into a human readable string
     */
    public static String format(long timestamp) {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * Formats the given file size in bytes into a human readable format;
     */
    public static String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return SIZE_FORMAT.format(size / Math.pow(1024, digitGroups)) + " " + SIZE_UNITS[digitGroups];
    }
}

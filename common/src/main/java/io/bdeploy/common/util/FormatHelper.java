package io.bdeploy.common.util;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.time.DurationFormatUtils;

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
     * Formats the given {@link Date} into a human readable {@link String}.
     */
    public static String formatDate(Date date) {
        return formatInstant(date.toInstant());
    }

    /**
     * Formats the given {@link Instant} into a human readable {@link String}.
     */
    public static String formatInstant(Instant temporal) {
        return FORMATTER.format(temporal);
    }

    /**
     * Formats the given duration into a human readable {@link String}.
     */
    public static String formatDuration(long timeInMillis) {
        String format = "HH 'h' mm 'min' ss 'sec' SSS 'ms'";
        if (timeInMillis >= 0) {
            return DurationFormatUtils.formatDuration(timeInMillis, format, true);
        }
        return "-" + DurationFormatUtils.formatDuration(-timeInMillis, format, true);
    }

    /**
     * Returns a human readable string of the given time. Only the most relevant unit is displayed.
     *
     * <pre>
     * Sample outputs:
     *              4 seconds
     *              15 minutes
     *              1 hour
     * </pre>
     *
     * @param duration The duration in milliseconds
     */
    public static String formatDurationBiggestOnly(long duration) {
        Duration d = Duration.ofMillis(duration);
        long days = d.toDays();
        if (days > 0) {
            return days + (days == 1 ? " day" : " days");
        }
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
     * Formats the given transfer rate into a human readable {@link String}.
     *
     * @param bytes The number of bytes that have been transfered
     * @param timeInMs The time in milliseconds that the transfer took
     */
    public static String formatTransferRate(long bytes, long timeInMs) {
        if (bytes <= 0 || timeInMs < 1000) {
            return "N/A";
        }
        double kBps = ((double) bytes) / timeInMs;
        if (kBps < 1000) {
            return TRANSFER_FORMAT.format(kBps) + " kB/s";
        }
        return TRANSFER_FORMAT.format(kBps / 1000.0) + " MB/s";
    }

    /**
     * Formats the given file size in bytes into a human readable {@link String}.
     *
     * @param size The size of the file in bytes
     */
    public static String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        int digitGroups = (int) (Math.log10(size) / 3);
        return SIZE_FORMAT.format(size / Math.pow(1000, digitGroups)) + ' ' + SIZE_UNITS[digitGroups];
    }
}

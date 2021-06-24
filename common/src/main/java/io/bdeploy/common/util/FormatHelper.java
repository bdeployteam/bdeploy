package io.bdeploy.common.util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
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
    private static final DecimalFormat TRANSFER_FORMAT = new DecimalFormat("#0.##");

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
     * Calculates and formats the transfer rate into a human readable string
     *
     * @param bytes the number of bytes that have been transfered
     * @param timeInMs the time in milliseconds that the transfer took
     */
    public static String formatTransferRate(long bytes, long timeInMs) {
        if (bytes <= 0 || timeInMs <= 1000) {
            return "";
        }
        double rateInKb = (bytes / 1024) / (timeInMs / 1000) * 8;
        if (rateInKb > 1000) {
            return TRANSFER_FORMAT.format(rateInKb / 1024.0) + " Mbps";
        }
        return TRANSFER_FORMAT.format(rateInKb) + " kbps";
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

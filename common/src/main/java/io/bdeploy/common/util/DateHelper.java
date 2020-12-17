package io.bdeploy.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

public class DateHelper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    private DateHelper() {
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

}

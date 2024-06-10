package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class FormatHelperTest {

    private static final char DECIMAL_SEPARATOR = ((DecimalFormat) DecimalFormat.getInstance()).getDecimalFormatSymbols()
            .getDecimalSeparator();

    @Test
    void formatDurationTest() {
        String result1 = FormatHelper.formatDuration(-1);
        assertEquals("59 min 59 sec 999 ms", result1);
        String result2 = FormatHelper.formatDuration(0);
        assertEquals("00 min 00 sec 000 ms", result2);
        String result3 = FormatHelper.formatDuration(1);
        assertEquals("00 min 00 sec 001 ms", result3);
        String result4 = FormatHelper.formatDuration(2);
        assertEquals("00 min 00 sec 002 ms", result4);
        String result5 = FormatHelper.formatDuration(Duration.ofSeconds(100).toMillis());
        assertEquals("01 min 40 sec 000 ms", result5);
        String result6 = FormatHelper.formatDuration(Duration.ofSeconds(100).toMillis() + 333);
        assertEquals("01 min 40 sec 333 ms", result6);
        String result7 = FormatHelper.formatDuration(Duration.ofMinutes(3).toMillis());
        assertEquals("03 min 00 sec 000 ms", result7);
        String result8 = FormatHelper.formatDuration(Duration.ofHours(30).toMillis());
        assertEquals("00 min 00 sec 000 ms", result8);
        String result9 = FormatHelper.formatDuration(Duration.ofHours(30).toMillis() + 500);
        assertEquals("00 min 00 sec 500 ms", result9);
    }

    @Test
    void formatRemainingTimeTest() {
        String result1 = FormatHelper.formatRemainingTime(-1);
        assertEquals("0 secs", result1);
        String result2 = FormatHelper.formatRemainingTime(0);
        assertEquals("0 secs", result2);
        String result3 = FormatHelper.formatRemainingTime(1);
        assertEquals("0 secs", result3);
        String result4 = FormatHelper.formatRemainingTime(2);
        assertEquals("0 secs", result4);
        String result5 = FormatHelper.formatRemainingTime(Duration.ofSeconds(100).toMillis());
        assertEquals("1 min", result5);
        String result6 = FormatHelper.formatRemainingTime(Duration.ofSeconds(100).toMillis() + 333);
        assertEquals("1 min", result6);
        String result7 = FormatHelper.formatRemainingTime(Duration.ofMinutes(3).toMillis());
        assertEquals("3 mins", result7);
        String result8 = FormatHelper.formatRemainingTime(Duration.ofHours(30).toMillis());
        assertEquals("30 hours", result8);
        String result9 = FormatHelper.formatRemainingTime(Duration.ofHours(30).toMillis() + 500);
        assertEquals("30 hours", result9);
    }

    @Test
    void formatTransferRateTest() {
        String resultBothZero = FormatHelper.formatTransferRate(0, 0);
        assertEquals("N/A", resultBothZero);

        String resultTimeZero1 = FormatHelper.formatTransferRate(-1, 0);
        assertEquals("N/A", resultTimeZero1);
        String resultTimeZero2 = FormatHelper.formatTransferRate(1, 0);
        assertEquals("N/A", resultTimeZero2);
        String resultTimeZero3 = FormatHelper.formatTransferRate(2, 0);
        assertEquals("N/A", resultTimeZero3);
        String resultTimeZero4 = FormatHelper.formatTransferRate(1_000, 0);
        assertEquals("N/A", resultTimeZero4);

        String resultBytesZero1 = FormatHelper.formatTransferRate(0, -1);
        assertEquals("N/A", resultBytesZero1);
        String resultBytesZero2 = FormatHelper.formatTransferRate(0, 1);
        assertEquals("N/A", resultBytesZero2);
        String resultBytesZero3 = FormatHelper.formatTransferRate(0, 2);
        assertEquals("N/A", resultBytesZero3);
        String resultBytesZero4 = FormatHelper.formatTransferRate(0, 1_000);
        assertEquals("N/A", resultBytesZero4);

        long oneSecondInMillis = Duration.ofSeconds(1).toMillis();
        String resultOneSecond1 = FormatHelper.formatTransferRate(1, oneSecondInMillis);
        assertEquals("0" + DECIMAL_SEPARATOR + "0 kB/s", resultOneSecond1);
        String resultOneSecond2 = FormatHelper.formatTransferRate(1_000, oneSecondInMillis);
        assertEquals("1" + DECIMAL_SEPARATOR + "0 kB/s", resultOneSecond2);
        String resultOneSecond3 = FormatHelper.formatTransferRate(33_521, oneSecondInMillis);
        assertEquals("33" + DECIMAL_SEPARATOR + "5 kB/s", resultOneSecond3);
        String resultOneSecond4 = FormatHelper.formatTransferRate(100_000, oneSecondInMillis);
        assertEquals("100" + DECIMAL_SEPARATOR + "0 kB/s", resultOneSecond4);
        String resultOneSecond5 = FormatHelper.formatTransferRate(1_000_000, oneSecondInMillis);
        assertEquals("1" + DECIMAL_SEPARATOR + "0 MB/s", resultOneSecond5);
        String resultOneSecond6 = FormatHelper.formatTransferRate(10_000_000, oneSecondInMillis);
        assertEquals("10" + DECIMAL_SEPARATOR + "0 MB/s", resultOneSecond6);
        String resultOneSecond7 = FormatHelper.formatTransferRate(1_000_000_000, oneSecondInMillis);
        assertEquals("1000" + DECIMAL_SEPARATOR + "0 MB/s", resultOneSecond7);

        String result1 = FormatHelper.formatTransferRate(1_000, Duration.ofSeconds(10).toMillis());
        assertEquals("0" + DECIMAL_SEPARATOR + "1 kB/s", result1);
        String result2 = FormatHelper.formatTransferRate(1_000, Duration.ofSeconds(3).toMillis());
        assertEquals("0" + DECIMAL_SEPARATOR + "3 kB/s", result2);
        String result3 = FormatHelper.formatTransferRate(565_883, 22_870);
        assertEquals("24" + DECIMAL_SEPARATOR + "7 kB/s", result3);
    }

    @Test
    void formatFileSizeTest() {
        String result1 = FormatHelper.formatFileSize(-1);
        assertEquals("0 B", result1);
        String result2 = FormatHelper.formatFileSize(0);
        assertEquals("0 B", result2);
        String result3 = FormatHelper.formatFileSize(1);
        assertEquals("1 B", result3);
        String result4 = FormatHelper.formatFileSize(2);
        assertEquals("2 B", result4);
        String result5 = FormatHelper.formatFileSize(123);
        assertEquals("123 B", result5);
        String result6 = FormatHelper.formatFileSize(1_234);
        assertEquals("1" + DECIMAL_SEPARATOR + "2 kB", result6);
        String result7 = FormatHelper.formatFileSize(12_345);
        assertEquals("12" + DECIMAL_SEPARATOR + "3 kB", result7);
        String result8 = FormatHelper.formatFileSize(23_348_567);
        assertEquals("23" + DECIMAL_SEPARATOR + "3 MB", result8);
        String result9 = FormatHelper.formatFileSize(76_577_657_234_567L);
        assertEquals("76" + DECIMAL_SEPARATOR + "6 TB", result9);
    }
}

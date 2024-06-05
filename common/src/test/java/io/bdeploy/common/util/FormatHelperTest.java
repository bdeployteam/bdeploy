package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class FormatHelperTest {

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
}

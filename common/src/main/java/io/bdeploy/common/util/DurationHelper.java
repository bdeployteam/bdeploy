package io.bdeploy.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DurationHelper {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("mm 'min' ss 'sec' SSS 'ms'");

    private DurationHelper() {
    }

    public static String formatDuration(long timeInMillis) {
        return SDF.format(new Date(timeInMillis));
    }

}

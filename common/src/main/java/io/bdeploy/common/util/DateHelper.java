package io.bdeploy.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {

    private DateHelper() {
    }

    /**
     * Formats the given date into a human readable string
     */
    public static String format(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyy HH:mm:ss");
        return sdf.format(date);
    }

}

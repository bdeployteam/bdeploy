package io.bdeploy.common.util;

import java.text.DecimalFormat;

/**
 * Helpers to format and or convert units.
 */
public class UnitHelper {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.#");
    private static final String[] SIZE_UNITS = new String[] { "B", "kB", "MB", "GB", "TB" };

    private UnitHelper() {
    }

    public static String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return SIZE_FORMAT.format(size / Math.pow(1024, digitGroups)) + " " + SIZE_UNITS[digitGroups];
    }

}

package io.bdeploy.common.util;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Determines OS specifics.
 */
public class OsHelper {

    public enum OperatingSystem {
        WINDOWS,
        LINUX,
        AIX,
        MACOS,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    private OsHelper() {
    }

    /**
     * @return the currently running {@link OperatingSystem}.
     */
    public static OperatingSystem getRunningOs() {
        String prop = System.getProperty("os.name").toUpperCase();
        if (prop.contains("WINDOWS")) {
            return OperatingSystem.WINDOWS;
        } else if (prop.contains("LINUX")) {
            return OperatingSystem.LINUX;
        } else if (prop.contains("AIX")) {
            return OperatingSystem.AIX;
        } else if (prop.contains("MAC")) {
            return OperatingSystem.MACOS;
        }

        throw new IllegalStateException("Unsupported OS: " + prop);
    }

}

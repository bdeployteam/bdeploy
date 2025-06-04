package io.bdeploy.common.util;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Determines OS specifics.
 */
public class OsHelper {

    /**
     * Operating system *including* architecture.
     * <p>
     * It would be nicer to have architecture separated, but this is a backwards-compatibility nightmare.
     */
    public enum OperatingSystem {
        WINDOWS, // this is X64
        LINUX, // this is X64
        LINUX_AARCH64,
        @Deprecated(forRemoval = true, since = "7.7.0")
        AIX,
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
        String arch = System.getProperty("os.arch").toUpperCase();
        if (prop.contains("WINDOWS")) {
            return OperatingSystem.WINDOWS;
        } else if (prop.contains("LINUX")) {
            if (arch.contains("AARCH64")) {
                return OperatingSystem.LINUX_AARCH64;
            }
            return OperatingSystem.LINUX;
        }

        throw new IllegalStateException("Unsupported OS: " + prop);
    }

}

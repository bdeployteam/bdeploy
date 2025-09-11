package io.bdeploy.common.util;

import java.util.EnumSet;

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

    private static final EnumSet<OperatingSystem> supportedOperatingSystems = EnumSet.of(OperatingSystem.WINDOWS,
            OperatingSystem.LINUX, OperatingSystem.LINUX_AARCH64);

    private OsHelper() {
    }

    public static boolean isSupported(OperatingSystem os) {
        return supportedOperatingSystems.contains(os);
    }

    public static boolean isNotSupported(OperatingSystem os) {
        return !isSupported(os);
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

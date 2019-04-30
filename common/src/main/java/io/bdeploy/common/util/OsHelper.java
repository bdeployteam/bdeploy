package io.bdeploy.common.util;

/**
 * Determines OS specifics.
 */
public class OsHelper {

    public enum OperatingSystem {
        WINDOWS,
        LINUX,
        AIX,
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
        }

        throw new IllegalStateException("Unsupported OS: " + prop);
    }

}

package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.Version;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;

/**
 * Helper class providing access to common folders.
 */
public class ClientPathHelper {

    /**
     * Name of the directory containing the launcher.
     */
    public static final String LAUNCHER_DIR = "launcher";

    private ClientPathHelper() {
    }

    /**
     * Returns the are where the user is permitted to write files required for the application to run.
     */
    public static Path getUserArea() {
        // Check if a specific directory should be used
        Path userArea = PathHelper.ofNullableStrig(System.getenv("BDEPLOY_USER_AREA"));
        if (userArea != null) {
            userArea = userArea.toAbsolutePath();
        }

        // On Windows we default to the local application data folder
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return Paths.get(System.getenv("LOCALAPPDATA"), "BDeploy");
        }

        // No user area
        return null;
    }

    /**
     * Returns the home directory for the given version. Each version will get its own directory where the launcher, the hive as
     * well as all apps are stored. Nothing is shared between different versions to prevent side-effects
     */
    public static Path getHome(Path root, Version version) {
        String name = "bdeploy-" + version.toString();
        return root.resolve(name);
    }

    /**
     * Returns the native launcher used to start the application.
     */
    public static Path getNativeLauncher(Path root, Version version) {
        // On Windows we are searching for a BDeploy.exe executable in the launcher directory
        Path launcherHome = root.resolve("launcher");
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return launcherHome.resolve("BDeploy.exe");
        }
        // On Linux and MAC the startup script is in the bin folder
        return launcherHome.resolve("bin").resolve("launcher");
    }

}

package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.Version;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Helper class providing access to common folders.
 */
public class ClientPathHelper {

    /**
     * Name of the directory containing the launcher.
     */
    public static final String LAUNCHER_DIR = "launcher";

    /**
     * Name of the environment variable defining the home-directory
     */
    public static final String BDEPLOY_HOME = "BDEPLOY_HOME";

    private ClientPathHelper() {
    }

    /**
     * Returns the home directory where the hive, the launcher as well as all apps are stored.
     */
    public static Path getBDeployHome() {
        Path rootDir = Paths.get(System.getProperty("user.home")).resolve(".bdeploy");
        String override = System.getenv(BDEPLOY_HOME);
        if (override != null && !override.isEmpty()) {
            rootDir = Paths.get(override);
        } else {
            override = System.getenv("LOCALAPPDATA");
            if (override != null && !override.isEmpty()) {
                rootDir = Paths.get(override).resolve("BDeploy");
            }
        }
        return rootDir.toAbsolutePath();
    }

    /**
     * Returns the home directory for the given version. Each version will get its own directory where the launcher, the hive as
     * well as all apps are stored. Nothing is shared between different versions to prevent side-effects
     */
    public static Path getHome(Version version) {
        String name = "bdeploy-" + version.toString();
        return getBDeployHome().resolve(name);
    }

    /**
     * Returns the native launcher used to start the application.
     */
    public static Path getNativeLauncher(Version version) {
        // On Windows we are searching for a BDeploy.exe executable in the launcher directory
        Path launcherHome = getHome(version).resolve(LAUNCHER_DIR);
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return launcherHome.resolve("BDeploy.exe");
        }
        // On Linux and MAC the startup script is in the bin folder
        return launcherHome.resolve("bin").resolve(LAUNCHER_DIR);
    }

    /**
     * Returns the native uninstaller used to remove the application.
     */
    public static Path getNativeUninstaller(Version version) {
        // On Windows we are searching for a Uninstaller.exe executable in the launcher directory
        Path launcherHome = getHome(version).resolve(LAUNCHER_DIR);
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return launcherHome.resolve("Uninstaller.exe");
        }
        // On Linux and MAC the remove script is in the bin folder
        return launcherHome.resolve("bin").resolve("uninstaller");
    }

}

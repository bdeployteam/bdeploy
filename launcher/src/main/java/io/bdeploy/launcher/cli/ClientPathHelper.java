package io.bdeploy.launcher.cli;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

/**
 * Helper class providing access to common folders.
 */
public class ClientPathHelper {

    /**
     * Name of the directory containing the launcher.
     */
    public static final String LAUNCHER_DIR = "launcher";

    /**
     * Name of the launch files.
     */
    public static final String LAUNCH_FILE_NAME = "launch.bdeploy";

    /**
     * Name of the launcher on Windows
     */
    public static final String WIN_LAUNCHER = "BDeploy.exe";

    /**
     * Name of the launcher on Linux
     */
    public static final String LINUX_LAUNCHER = LAUNCHER_DIR;

    private ClientPathHelper() {
    }

    /**
     * Returns the are where the user is permitted to write files required for the application to run.
     */
    public static Path getUserArea() {
        // Check if a specific directory should be used
        Path userArea = PathHelper.ofNullableStrig(System.getenv("BDEPLOY_USER_AREA"));
        if (userArea != null) {
            return userArea.toAbsolutePath();
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
     * Returns a list of all hives in the given directory.
     */
    public static List<Path> getHives(Path rootDir) throws IOException {
        List<Path> hives = new ArrayList<>();
        hives.add(rootDir.resolve("bhive"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir,
                p -> p.getFileName().toString().toLowerCase().startsWith("bdeploy-"))) {
            Iterator<Path> dirs = stream.iterator();
            while (dirs.hasNext()) {
                Path nestedRoot = dirs.next();
                Path nestedHive = nestedRoot.resolve("bhive");
                if (!nestedHive.toFile().isDirectory()) {
                    continue;
                }
                hives.add(nestedHive);
            }
        }
        return hives;
    }

    /**
     * Returns the native launcher used to start the application.
     */
    public static Path getNativeLauncher(Path root) {
        // On Windows we are searching for a BDeploy.exe executable in the launcher directory
        Path launcherHome = root.resolve(LAUNCHER_DIR);
        if (OsHelper.getRunningOs() == OperatingSystem.WINDOWS) {
            return launcherHome.resolve(WIN_LAUNCHER);
        }
        // On Linux and MAC the startup script is in the bin folder
        return launcherHome.resolve("bin").resolve(LINUX_LAUNCHER);
    }

    /**
     * Returns the descriptor for the given application
     */
    public static Path getOrCreateClickAndStart(Path rootDir, ClickAndStartDescriptor clickAndStart) throws IOException {
        Path appDir = getAppHomeDir(rootDir, clickAndStart);
        Path launchFile = appDir.resolve(LAUNCH_FILE_NAME);

        // Create if not existing
        if (!launchFile.toFile().isFile()) {
            PathHelper.mkdirs(appDir);
            Files.write(launchFile, StorageHelper.toRawBytes(clickAndStart));
        }
        return launchFile;
    }

    /**
     * Returns the home directory for the given application
     */
    public static Path getAppHomeDir(Path rootDir, ClickAndStartDescriptor clickAndStart) {
        return rootDir.resolve("apps").resolve(clickAndStart.applicationId);
    }

}

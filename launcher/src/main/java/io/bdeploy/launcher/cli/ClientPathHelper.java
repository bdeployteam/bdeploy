package io.bdeploy.launcher.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper class providing access to common folders.
 */
public class ClientPathHelper {

    private ClientPathHelper() {
    }

    /**
     * Returns the home directory where the hive, the launcher as well as all apps are stored.
     */
    public static Path getBDeployHome() {
        Path rootDir = Paths.get(System.getProperty("user.home")).resolve(".bdeploy");
        String override = System.getenv("BDEPLOY_HOME");
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

}

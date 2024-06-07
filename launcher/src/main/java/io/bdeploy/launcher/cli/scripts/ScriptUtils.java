package io.bdeploy.launcher.cli.scripts;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Contains utility methods for scripts.
 */
public class ScriptUtils {

    private ScriptUtils() {
    }

    public static String getBDeployFileAssocId(String applicationId) {
        return "BDeploy." + applicationId + ".1";
    }

    public static String getFullFileExtension(String fileExtension) {
        String prefix = ".";
        return fileExtension.startsWith(prefix) ? fileExtension : prefix + fileExtension;
    }

    public static String getStartScriptIdentifier(OperatingSystem os, String identifier) {
        if (identifier == null) {
            return null;
        }

        if (os == OperatingSystem.WINDOWS) {
            String fileExtension = ".bat";
            return identifier.endsWith(fileExtension) ? identifier : identifier + fileExtension;
        }
        return identifier;
    }

    public static String getFileAssocIdentifier(OperatingSystem os, String identifier) {
        if (identifier == null) {
            return null;
        }

        if (!identifier.startsWith("start.")) {
            if (identifier.startsWith(".")) {
                identifier = "start" + identifier;
            } else {
                identifier = "start." + identifier;
            }
        }

        if (os == OperatingSystem.WINDOWS) {
            String fileExtension = ".bat";
            return identifier.endsWith(fileExtension) ? identifier : identifier + fileExtension;
        }
        return identifier;
    }
}

package io.bdeploy.launcher.cli;

import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.core.util.ContextDataProvider;

public class LauncherLoggingContextDataProvider implements ContextDataProvider {

    private static final String LOG_DIR_KEY = "TARGET_LOG_DIR";
    private static final String LOG_NAME_KEY = "TARGET_LOG_FILE";

    private static String logDir = null;
    private static String logBaseName = null;

    @Override
    public Map<String, String> supplyContextData() {
        if (logDir != null && logBaseName != null) {
            return Map.of(LOG_DIR_KEY, logDir, LOG_NAME_KEY, logBaseName);
        }
        return Collections.emptyMap();
    }

    public static void setLogDir(String dir) {
        logDir = dir;
    }

    public static void setLogFileBaseName(String name) {
        logBaseName = name;
    }
}

package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionHelper {

    public static final String UNKNOWN = "Unknown";

    private VersionHelper() {
    }

    public static String readVersion() {
        Properties props = readProperties();
        if (props == null) {
            return UNKNOWN;
        }
        return props.getProperty("version");
    }

    public static Properties readProperties() {
        try (InputStream is = VersionHelper.class.getResourceAsStream("/version.properties")) {
            Properties p = new Properties();
            p.load(is);

            return p;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

}

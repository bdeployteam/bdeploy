package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionHelper {

    public static final String UNKNOWN = "Unknown";

    public static String readVersion() {
        try (InputStream is = VersionHelper.class.getResourceAsStream("/version.properties")) {
            Properties p = new Properties();
            p.load(is);

            return p.getProperty("version");
        } catch (IOException | RuntimeException e) {
            return UNKNOWN;
        }
    }

}

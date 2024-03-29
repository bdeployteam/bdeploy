package io.bdeploy.ui.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.codec.binary.Base64;

import io.bdeploy.bhive.util.StorageHelper;

/**
 * Provides helper methods to embed the configuration into the Windows installer executable. The installer is expecting that the
 * configuration is wrapped by some special start and end tags. The actual location in the file is not relevant.
 */
public class WindowsInstaller {

    private static final String START_BDEPLOY = "###START_BDEPLOY###";
    private static final String START_CONFIG = "###START_CONFIG###";
    private static final String END_CONFIG = "###END_CONFIG###";
    private static final String END_BDEPLOY = "###END_BDEPLOY###";

    private WindowsInstaller() {
        // static helper only.
    }

    /**
     * Embeds the given configuration into the given signed PE/COFF executable.
     */
    public static void embedConfig(Path file, WindowsInstallerConfig config) throws IOException {
        String configAsString = new String(StorageHelper.toRawBytes(config), StandardCharsets.UTF_8);
        String encodedConfig = Base64.encodeBase64String(configAsString.getBytes(StandardCharsets.UTF_8));

        // Wrap the configuration with start / end tags
        StringBuilder builder = new StringBuilder();
        builder.append(START_BDEPLOY);
        builder.append(START_CONFIG);
        builder.append(encodedConfig);
        builder.append(END_CONFIG);
        builder.append(END_BDEPLOY);

        // Update the executable
        WindowsExecutableUtils.embed(file, builder.toString().getBytes(StandardCharsets.UTF_8));
    }

}

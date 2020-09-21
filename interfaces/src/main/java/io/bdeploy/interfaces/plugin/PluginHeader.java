package io.bdeploy.interfaces.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;

import io.bdeploy.api.plugin.v1.Plugin;
import io.bdeploy.common.util.StringHelper;

public class PluginHeader {

    public final String mainClass;
    public final String name;
    public final String version;
    public final boolean sorter;

    private PluginHeader(String mainClass, String name, String version, boolean sorter) {
        this.mainClass = mainClass;
        this.name = name;
        this.version = version;
        this.sorter = sorter;
    }

    public static PluginHeader read(InputStream is) throws IOException {
        try (JarInputStream jis = new JarInputStream(is)) {
            Attributes mainAttributes = jis.getManifest().getMainAttributes();
            String mainClass = mainAttributes.getValue(Plugin.PLUGIN_CLASS_HEADER);
            String name = mainAttributes.getValue(Plugin.PLUGIN_NAME_HEADER);
            String version = mainAttributes.getValue(Plugin.PLUGIN_VERSION_HEADER);
            String sorter = mainAttributes.getValue(Plugin.PLUGIN_SORTER_HEADER);

            if (mainClass == null || name == null) {
                throw new IllegalStateException("The plugin must define the '" + Plugin.PLUGIN_CLASS_HEADER + "' and '"
                        + Plugin.PLUGIN_NAME_HEADER + "' headers.");
            }

            if (version == null) {
                version = "undefined";
            }

            return new PluginHeader(mainClass, name, version, !StringHelper.isNullOrEmpty(sorter));
        }
    }

}

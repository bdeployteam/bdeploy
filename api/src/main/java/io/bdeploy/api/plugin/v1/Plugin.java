package io.bdeploy.api.plugin.v1;

import java.util.Collection;
import java.util.Collections;

/**
 * Base class for all plugins.
 * <p>
 * Plugins are registered on the server using a unique plugin ID. This ID is of no direct interest to the plugin. All endpoints
 * and assets will be served by the server in a context path which reflects this ID. A plugin with an identical (i.e. hash of JAR
 * identical) ID will be served only once, even if registered multiple times.
 */
public abstract class Plugin {

    /**
     * The name of the MANIFEST.MF header which must be present in the JAR file of the plugin pointing to the fully qualified name
     * of the {@link Plugin} subclass within the JAR.
     */
    public static final String PLUGIN_CLASS_HEADER = "BDeploy-Plugin";

    /**
     * The name of the MANIFEST.MF header which must be present in the JAR file of the plugin setting the human readable name of
     * the plugin
     */
    public static final String PLUGIN_NAME_HEADER = "BDeploy-PluginName";

    /**
     * The name of the MANIFEST.MF header which must be present in the JAR file of the plugin setting the human readable version
     * of the plugin
     */
    public static final String PLUGIN_VERSION_HEADER = "BDeploy-PluginVersion";

    /**
     * A header which is used to discover plugins which allow product version sorting without actually loading those plugins.
     */
    public static final String PLUGIN_SORTER_HEADER = "BDeploy-PluginHasVersionSorter";

    /**
     * @return a collection of JAX-RS component classes which should be registered as resources.
     */
    public Collection<Class<?>> getComponentClasses() {
        return Collections.emptyList();
    }

    /**
     * @return a collection of JAX-RS component objects which should be registered with the server.
     */
    public Collection<Object> getComponentObjects() {
        return Collections.emptyList();
    }

    /**
     * @return a collection of {@link PluginAssets} which describe the plugin's asset paths to be hosted on the server.
     */
    public Collection<PluginAssets> getAssets() {
        return Collections.emptyList();
    }

    /**
     * @return a collection of {@link CustomEditor} which describe all custom editors which can be loaded from this plugin.
     */
    public Collection<CustomEditor> getCustomEditors() {
        return Collections.emptyList();
    }

    /**
     * @return a {@link CustomProductVersionSorter} which provides custom version comparision for the product.
     */
    public CustomProductVersionSorter getCustomSorter() {
        return null;
    }

}

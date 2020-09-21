package io.bdeploy.interfaces.plugin;

import java.util.List;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;

public interface PluginManager {

    /**
     * @return a list of all loaded plugins.
     */
    public List<PluginInfoDto> getPlugins();

    /**
     * @param id the id of the plugin to check.
     * @return whether a plugin with the given ID is loaded.
     */
    public boolean isLoaded(ObjectId id);

    /**
     * Load a global plugin manually, e.g. after uploading a new one at runtime.
     *
     * @param id the plugins id in the minions {@link BHive}.
     * @return the loaded plugins information.
     */
    public PluginInfoDto loadGlobalPlugin(ObjectId id);

    /**
     * Load a local plugin from a {@link BHive} using the plugins {@link ObjectId}.
     *
     * @param source the source {@link BHive} to load from
     * @param id the {@link ObjectId} of the plugin JAR file in the given {@link BHive}.
     *            {@link ObjectId}. This should usually be the {@link Manifest} which contains the plugin.
     * @param product the product where the plugin is loaded from. It is added to the list of "requestors" for the plugin.
     * @return the loaded plugins information.
     */
    public PluginInfoDto load(BHive source, ObjectId id, Manifest.Key product);

    /**
     * @param source the source {@link BHive}.
     * @param id the {@link ObjectId} of the plugin JAR to load the header from.
     * @return the loaded {@link PluginHeader}. In case the plugin is already loaded, the previously loaded header is returned.
     */
    public PluginHeader loadHeader(BHive source, ObjectId id);

    /**
     * Removes the product from all loaded plugin's "requestors". If a product-bound plugin has zero requestors left, it will be
     * unloaded.
     *
     * @param product the product which is removed.
     */
    public void unloadProduct(Manifest.Key product);

    /**
     * @param id the id of the plugin to unload
     */
    public void unload(ObjectId id);
}

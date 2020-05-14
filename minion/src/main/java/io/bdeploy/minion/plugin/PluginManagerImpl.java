package io.bdeploy.minion.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.plugin.v1.Plugin;
import io.bdeploy.api.plugin.v1.PluginAssets;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.plugin.PluginHeader;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.plugin.PluginManifest;
import io.bdeploy.jersey.JerseyServer;

/**
 * Keeps track of all loaded plugins in the system.
 */
public class PluginManagerImpl implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerImpl.class);

    private final BHive globalHive;
    private final Map<ObjectId, PluginInternalHandle> loaded = new TreeMap<>();
    private final JerseyServer server;

    public PluginManagerImpl(BHive globalHive, JerseyServer server) {
        this.globalHive = globalHive;
        this.server = server;

        loadGlobals();
    }

    /**
     * Loads all plugins which are globally registered in the minions {@link BHive}.
     */
    private void loadGlobals() {
        for (Manifest.Key key : PluginManifest.scan(globalHive)) {
            try {
                loadGlobalPlugin(PluginManifest.of(globalHive, key).getPlugin());
            } catch (Exception e) {
                log.warn("Cannot load global plugin from " + key, e);
            }
        }
    }

    @Override
    public PluginInfoDto loadGlobalPlugin(ObjectId id) {
        if (loaded.containsKey(id)) {
            log.warn("Skipping load of {}, already loaded from another location.", id);
            return null;
        }

        PluginInternalHandle handle = loadFile(globalHive.execute(new FindFileOperation().setObject(id)));
        handle.id = id;
        handle.global = true;

        register(id, handle);

        return getInfoFromHandle(handle);
    }

    @Override
    public List<PluginInfoDto> getPlugins() {
        List<PluginInfoDto> globs = new ArrayList<>();
        for (Map.Entry<ObjectId, PluginInternalHandle> entry : loaded.entrySet()) {
            globs.add(getInfoFromHandle(entry.getValue()));
        }
        return globs;
    }

    @Override
    public boolean isLoaded(ObjectId id) {
        return loaded.containsKey(id);
    }

    @Override
    public PluginInfoDto load(BHive source, ObjectId id, Manifest.Key product) {
        PluginInternalHandle handle = loaded.get(id);
        if (handle == null) {
            Path pluginPath = source.execute(new FindFileOperation().setObject(id));
            handle = loadFile(pluginPath);
            handle.id = id;

            register(id, handle);
        }
        handle.requestedFrom.add(product);
        return getInfoFromHandle(handle);
    }

    @Override
    public void unloadProduct(Key product) {
        List<ObjectId> toUnload = new ArrayList<>();
        for (Map.Entry<ObjectId, PluginInternalHandle> entry : loaded.entrySet()) {
            if (entry.getValue().global) {
                continue;
            }

            entry.getValue().requestedFrom.remove(product);

            if (entry.getValue().requestedFrom.isEmpty()) {
                toUnload.add(entry.getKey());
            }
        }
        toUnload.forEach(this::unload);
    }

    private synchronized void register(ObjectId id, PluginInternalHandle handle) {
        try {
            resolveConflicts(handle);
        } catch (Exception e) {
            handle.plugin = null;
            if (handle.pluginLoader != null) {
                try {
                    handle.pluginLoader.close();
                } catch (IOException ioe) {
                    log.warn("Cannot clean up plugin handle for {}", id, ioe);
                }
            }
            throw e;
        }

        registerPluginResources(handle);
        loaded.put(id, handle);
    }

    private void resolveConflicts(PluginInternalHandle handle) {
        List<PluginInternalHandle> toUnload = new ArrayList<>();

        // check for a plugin with the same name.
        for (Map.Entry<ObjectId, PluginInternalHandle> entry : loaded.entrySet()) {
            if (entry.getValue().header.name.equals(handle.header.name)) {

                // Global plugins: only a single plugin with a given name/version is allowed.
                //                 will unload local plugins with the same name when loaded.
                // Local plugins: refused if a global plugin with the same name is loaded.
                //                refused if there is a local plugin already with same name & version, but different ID.

                if (handle.global) {
                    if (entry.getValue().global) {
                        if (handle.header.version.equals(entry.getValue().header.version)) {
                            throw new IllegalStateException("Two global plugins with the same name/version found: "
                                    + handle.header.name + " (" + handle.header.version + ")");
                        }
                    } else {
                        log.info("Replacing local plugin with global one: {}", entry.getValue().header.name);
                        toUnload.add(entry.getValue());
                    }
                } else {
                    if (entry.getValue().global) {
                        throw new IllegalStateException(
                                "Will not load local plugin with same name as global plugin: " + handle.header.name);
                    }
                }
            }
        }

        for (PluginInternalHandle unload : toUnload) {
            unloadInternal(unload);
        }
    }

    private PluginInfoDto getInfoFromHandle(PluginInternalHandle handle) {
        return new PluginInfoDto(handle.id, handle.header.name, handle.header.version, handle.global,
                new ArrayList<>(handle.plugin.getCustomEditors()));
    }

    /**
     * Creates a new {@link PluginInternalHandle} by loading the given JAR file, creating a classloader and instantiating the
     * plugin.
     */
    private PluginInternalHandle loadFile(Path plugin) {
        PluginHeader hdr;
        try (InputStream is = Files.newInputStream(plugin)) {
            hdr = PluginHeader.read(is);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load plugin from " + plugin, e);
        }

        PluginInternalHandle handle = new PluginInternalHandle();
        try {
            handle.header = hdr;
            handle.pluginLoader = new URLClassLoader(new URL[] { plugin.toUri().toURL() }, Plugin.class.getClassLoader());
            handle.plugin = Plugin.class.cast(handle.pluginLoader.loadClass(hdr.mainClass).getConstructor().newInstance());
            return handle;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load plugin from " + plugin, e);
        }
    }

    /**
     * Registers all resources that need to be registered against the {@link JerseyServer}.
     *
     * @param handle the handle to register resources from
     */
    private void registerPluginResources(PluginInternalHandle handle) {
        String contextRoot = "/api/plugins/" + handle.id;

        if (!handle.plugin.getComponentClasses().isEmpty() || !handle.plugin.getComponentObjects().isEmpty()) {
            // create a JAX-RS configuration
            ResourceConfig config = new ResourceConfig();
            for (Class<?> clazz : handle.plugin.getComponentClasses()) {
                config.register(clazz);
            }
            for (Object obj : handle.plugin.getComponentObjects()) {
                config.register(obj);
            }
            server.registerDefaultResources(config);

            // create, register, remember a handler for JAX-RS. This is always registered on the plugin's root context path.
            HttpHandler jaxRs = ContainerFactory.createContainer(HttpHandler.class, config);
            server.addHandler(jaxRs, HttpHandlerRegistration.builder().contextPath(contextRoot).urlPattern("/").build());
            handle.createdHandlers.add(jaxRs);
        }

        for (PluginAssets assets : handle.plugin.getAssets()) {
            // create, register, remember file serving handler. plugin requested asset path is added to the context path to assure
            // propper lookups in the handler. The served root in the JAR should be the relative root as well ('/*').
            HttpHandler files = server.getCorsAwareStaticClassPathHandler(handle.pluginLoader,
                    withTrailingSlash(assets.getJarPath()));
            server.addHandler(files, HttpHandlerRegistration.builder()
                    .contextPath(contextRoot + withLeadingSlash(assets.getUrlPath())).urlPattern("/*").build());
            handle.createdHandlers.add(files);
        }
    }

    private String withLeadingSlash(String string) {
        if (string.startsWith("/")) {
            return string;
        }
        return "/" + string;
    }

    private String withTrailingSlash(String string) {
        if (string.endsWith("/")) {
            return string;
        }
        return string + "/";
    }

    @Override
    public void unload(ObjectId id) {
        PluginInternalHandle handle = loaded.get(id);
        if (handle == null) {
            return;
        }

        unloadInternal(handle);
    }

    private void unloadInternal(PluginInternalHandle handle) {
        try {
            loaded.remove(handle.id);

            for (HttpHandler handler : handle.createdHandlers) {
                server.removeHandler(handler);
            }
            unloadFile(handle);
        } catch (Exception e) {
            log.error("Cannot unload plugin {}", handle.id, e);
        }
    }

    private void unloadFile(PluginInternalHandle handle) throws IOException {
        handle.plugin = null;
        if (handle.pluginLoader != null) {
            handle.pluginLoader.close();
        }
    }

    private static class FindFileOperation extends BHive.Operation<Path> {

        private ObjectId object;

        @Override
        public Path call() throws Exception {
            return getObjectManager().db((db) -> {
                return db.getObjectFile(object);
            });
        }

        public FindFileOperation setObject(ObjectId id) {
            this.object = id;
            return this;
        }

    }

}

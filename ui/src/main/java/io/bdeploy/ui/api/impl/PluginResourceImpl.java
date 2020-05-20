package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.plugin.v1.CustomEditor;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.plugin.PluginHeader;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.interfaces.plugin.PluginManager;
import io.bdeploy.interfaces.plugin.PluginManifest;
import io.bdeploy.ui.api.PluginResource;

public class PluginResourceImpl implements PluginResource {

    private static final Logger log = LoggerFactory.getLogger(PluginResourceImpl.class);

    @Inject
    private PluginManager manager;

    @Inject
    private BHiveRegistry reg;

    @Override
    public List<PluginInfoDto> getLoadedPlugins() {
        return manager.getPlugins();
    }

    @Override
    public List<PluginInfoDto> getNotLoadedGlobalPlugin() {
        List<PluginInfoDto> result = new ArrayList<>();

        BHive hive = getDefaultHive();
        for (Manifest.Key key : PluginManifest.scan(hive)) {
            PluginManifest mf = PluginManifest.of(hive, key);

            if (!manager.isLoaded(mf.getPlugin())) {
                try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(mf.getPlugin()))) {
                    PluginHeader hdr = PluginHeader.read(is);
                    result.add(new PluginInfoDto(mf.getPlugin(), hdr.name, hdr.version, true, Collections.emptyList()));
                } catch (IOException e) {
                    log.error("Cannot load plugin " + key, e);
                }
            }
        }

        return result;
    }

    @Override
    public PluginInfoDto getPluginForEditor(String group, String type, Manifest.Key product) {
        // check plugins from the product.
        BHive hive = reg.get(group);
        if (hive == null) {
            throw new WebApplicationException("Instance Group not found: " + group, Status.NOT_FOUND);
        }
        ProductManifest pm = ProductManifest.of(hive, product);
        for (ObjectId plugin : pm.getPlugins()) {
            try {
                PluginInfoDto info = manager.load(hive, plugin, product);
                if (info.editors.stream().filter(e -> e.getTypeName().equals(type)).findAny().isPresent()) {
                    return info;
                }
            } catch (Exception e) {
                log.warn("Cannot load plugin from {}: {}: {}", product, plugin, e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception", e);
                }
            }
        }

        // Fallback if there is none in the product, check if there is one in the globals.
        PluginInfoDto candidate = null;
        for (PluginInfoDto info : getLoadedPlugins()) {
            if (!info.global) {
                // only allow global plugins, not local ones from OTHER products which did not match the ID above already.
                continue;
            }

            boolean hasEditor = false;
            for (CustomEditor editor : info.editors) {
                if (editor.getTypeName().equals(type)) {
                    hasEditor = true;
                    break;
                }
            }

            if (hasEditor) {
                if (candidate == null || candidate.version.equals("unknown") || isNewerVersion(candidate.version, info.version)) {
                    candidate = info;
                }
            }
        }

        if (candidate != null) {
            return candidate;
        }

        throw new WebApplicationException("Cannot find editor plugin for " + type, Status.NOT_FOUND);
    }

    /**
     * @return whether v2 is newer than v1
     */
    private boolean isNewerVersion(String v1, String v2) {
        try {
            Version ver1 = VersionHelper.parse(v1);
            Version ver2 = VersionHelper.parse(v2);

            return ver1.compareTo(ver2) < 0;
        } catch (Exception e) {
            return v1.compareTo(v2) < 0;
        }
    }

    @Override
    public void unloadPlugin(ObjectId id) {
        manager.unload(id);
    }

    @Override
    public void loadGlobalPlugin(ObjectId id) {
        manager.loadGlobalPlugin(id);
    }

    private BHive getDefaultHive() {
        return reg.get(JerseyRemoteBHive.DEFAULT_NAME);
    }

    @Override
    public PluginInfoDto uploadGlobalPlugin(InputStream inputStream, boolean replace) {
        BHive hive = getDefaultHive();

        try {
            byte[] bytes = StreamHelper.read(inputStream);

            if (replace) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    PluginHeader hdr = PluginHeader.read(bais);

                    // unload in case it is loaded
                    for (PluginInfoDto x : getLoadedPlugins()) {
                        if (x.name.equals(hdr.name) && x.version.equals(hdr.version)) {
                            manager.unload(x.id);
                        }
                    }

                    // remove it completely.
                    for (PluginInfoDto x : getNotLoadedGlobalPlugin()) {
                        if (x.name.equals(hdr.name) && x.version.equals(hdr.version)) {
                            deleteGlobalPlugin(x.id);
                        }
                    }
                }
            }

            PluginManifest.Builder builder = new PluginManifest.Builder();
            builder.setData(bytes);
            Manifest.Key key = builder.insert(hive);

            return manager.loadGlobalPlugin(PluginManifest.of(hive, key).getPlugin());
        } catch (IOException e) {
            throw new WebApplicationException("Cannot load plugin", e, Status.BAD_REQUEST);
        }
    }

    @Override
    public void deleteGlobalPlugin(ObjectId id) {
        BHive hive = getDefaultHive();

        manager.unload(id);

        for (Manifest.Key key : PluginManifest.scan(hive)) {
            PluginManifest pm = PluginManifest.of(hive, key);
            if (pm.getPlugin().equals(id)) {
                hive.execute(new ManifestDeleteOperation().setToDelete(key));
            }
        }
    }

}

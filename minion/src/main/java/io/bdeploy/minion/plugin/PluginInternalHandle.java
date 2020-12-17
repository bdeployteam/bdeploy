package io.bdeploy.minion.plugin;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.glassfish.grizzly.http.server.HttpHandler;

import io.bdeploy.api.plugin.v1.Plugin;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.plugin.PluginHeader;

/**
 * Internal management structure which holds all required information regarding a single loaded plugin.
 */
class PluginInternalHandle {

    public ObjectId id;
    public PluginHeader header;
    public Plugin plugin;
    public boolean global;
    public URLClassLoader pluginLoader;
    public List<HttpHandler> createdHandlers = new ArrayList<>();
    public SortedSet<Manifest.Key> requestedFrom = new TreeSet<>();

}

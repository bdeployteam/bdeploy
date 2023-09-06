package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.plugin.PluginInfoDto;
import io.bdeploy.jersey.Scope;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/plugin-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PluginResource {

    /**
     * @return all plugins
     */
    @GET
    @Path("/list")
    public List<PluginInfoDto> getPlugins();

    /**
     * @return all currently loaded plugins
     */
    @GET
    @Path("/list-loaded")
    public List<PluginInfoDto> getLoadedPlugins();

    /**
     * @return a list of all global plugins which are <b>not</b> currently loaded.
     */
    @GET
    @Path("/list-unloaded-global")
    public List<PluginInfoDto> getNotLoadedGlobalPlugin();

    /**
     * @return info for all of the products plugins, loaded and not loaded
     */
    @POST
    @Path("/list-product-plugins/{group}")
    public List<PluginInfoDto> getProductPlugins(@Scope @PathParam("group") String group, Manifest.Key product);

    /**
     * @param product a product from which to lookup and load local plugins.
     * @return a list of plugins, all global and matching local ones from the product.
     */
    @POST
    @Path("/get-editor/{group}/{type}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public PluginInfoDto getPluginForEditor(@Scope @PathParam("group") String group, @PathParam("type") String type,
            Manifest.Key product);

    @POST
    @Path("/list-editor-types/{group}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public Set<String> getEditorTypes(@Scope @PathParam("group") String group, Manifest.Key product);

    /**
     * @param id unload a certain plugin.
     */
    @POST
    @Path("/unload")
    @RequiredPermission(permission = Permission.ADMIN)
    public void unloadPlugin(ObjectId id);

    /**
     * @param id the id of a currently unloaded global plugin.
     */
    @POST
    @Path("/load-global")
    @RequiredPermission(permission = Permission.ADMIN)
    public void loadGlobalPlugin(ObjectId id);

    /**
     * @param inputStream the data for the plugin.
     * @return the info the the uploaded, installed and loaded plugin.
     */
    @POST
    @Path("/upload-global")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.ADMIN)
    public PluginInfoDto uploadGlobalPlugin(@FormDataParam("plugin") InputStream inputStream,
            @QueryParam("replace") boolean replace);

    /**
     * @param id the id of the plugin to remove.
     */
    @POST
    @Path("/delete-global")
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteGlobalPlugin(ObjectId id);
}

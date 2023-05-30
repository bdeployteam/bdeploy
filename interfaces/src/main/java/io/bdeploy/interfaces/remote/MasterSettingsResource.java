package io.bdeploy.interfaces.remote;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.settings.CustomAttributeDescriptor;
import io.bdeploy.interfaces.settings.WebAuthSettingsDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/master/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterSettingsResource {

    @GET
    public SettingsConfiguration getSettings();

    @GET
    @Unsecured
    @Path("/web-auth")
    public WebAuthSettingsDto getAuthSettings();

    @POST
    @RequiredPermission(permission = Permission.ADMIN)
    public void setSettings(SettingsConfiguration settings);

    @POST
    @Path("/groupAttributes")
    public void mergeInstanceGroupAttributesDescriptors(List<CustomAttributeDescriptor> attributes);
}

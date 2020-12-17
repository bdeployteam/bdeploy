package io.bdeploy.interfaces.remote;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.settings.CustomAttributeDescriptor;

@Path("/master/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterSettingsResource {

    @GET
    public SettingsConfiguration getSettings();

    @POST
    public void setSettings(SettingsConfiguration settings);

    @POST
    @Path("/groupAttributes")
    public void mergeInstanceGroupAttributesDescriptors(List<CustomAttributeDescriptor> attributes);
}

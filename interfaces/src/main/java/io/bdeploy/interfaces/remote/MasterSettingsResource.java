package io.bdeploy.interfaces.remote;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.settings.CustomPropertyDescriptor;

@Path("/master/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MasterSettingsResource {

    @GET
    public SettingsConfiguration getSettings();

    @POST
    public void setSettings(SettingsConfiguration settings);

    @POST
    @Path("/groupProperties")
    public void mergeInstanceGroupPropertyDescriptors(List<CustomPropertyDescriptor> properties);
}

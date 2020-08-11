package io.bdeploy.interfaces.remote;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.interfaces.configuration.SettingsConfiguration;
import io.bdeploy.interfaces.remote.versioning.VersionMismatchDetect;

@Path("/master/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@VersionMismatchDetect
public interface MasterSettingsResource {

    @GET
    public SettingsConfiguration getAuthenticationSettings();

    @POST
    public void setAuthenticationSettings(SettingsConfiguration settings);

}

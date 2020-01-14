package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.common.security.RequiredCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.jersey.ActivityScope;

@Path("/softwarerepository")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareRepositoryResource {

    @GET
    public List<SoftwareRepositoryConfiguration> list();

    @PUT
    @RequiredCapability(capability = Capability.ADMIN)
    public void create(SoftwareRepositoryConfiguration config);

    @GET
    @Path("/{repo}")
    public SoftwareRepositoryConfiguration read(@ActivityScope @PathParam("repo") String repo);

    @POST
    @Path("/{repo}")
    @RequiredCapability(capability = Capability.ADMIN)
    public void update(@ActivityScope @PathParam("repo") String repo, SoftwareRepositoryConfiguration config);

    @DELETE
    @Path("/{repo}")
    @RequiredCapability(capability = Capability.ADMIN)
    public void delete(@ActivityScope @PathParam("repo") String repo);

    @Path("/{softwareRepository}/content")
    public SoftwareResource getSoftwareResource(@ActivityScope @PathParam("softwareRepository") String softwareRepository);

}

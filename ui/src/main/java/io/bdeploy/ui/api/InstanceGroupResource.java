package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.common.security.RequiredCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.InstanceClientAppsDto;

@Path("/group")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceGroupResource {

    @GET
    public List<InstanceGroupConfiguration> list();

    @PUT
    @RequiredCapability(capability = Capability.ADMIN)
    public void create(InstanceGroupConfiguration config);

    @GET
    @Path("/{group}")
    @RequiredCapability(capability = Capability.READ, scope = "group")
    public InstanceGroupConfiguration read(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}")
    @RequiredCapability(capability = Capability.WRITE, scope = "group")
    public void update(@ActivityScope @PathParam("group") String group, InstanceGroupConfiguration config);

    @DELETE
    @Path("/{group}")
    @RequiredCapability(capability = Capability.ADMIN)
    public void delete(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredCapability(capability = Capability.WRITE, scope = "group")
    public void updateImage(@ActivityScope @PathParam("group") String group, @FormDataParam("image") InputStream imageData);

    @GET
    @Path("/{group}/image")
    @Produces("image/png")
    @Unsecured // required to allow requests from browser directly (e.g. CSS).
    public InputStream readImage(@ActivityScope @PathParam("group") String group);

    /**
     * Create a new unique (in the given group) UUID for an instance.
     */
    @GET
    @Path("/{group}/new-uuid")
    @RequiredCapability(capability = Capability.READ, scope = "group")
    public String createUuid(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/instance")
    @RequiredCapability(capability = Capability.READ, scope = "group")
    public InstanceResource getInstanceResource(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/product")
    @RequiredCapability(capability = Capability.READ, scope = "group")
    public ProductResource getProductResource(@ActivityScope @PathParam("group") String group);

    @GET
    @Path("/{group}/client-apps")
    @RequiredCapability(capability = Capability.READ, scope = "group")
    public Collection<InstanceClientAppsDto> listClientApps(@ActivityScope @PathParam("group") String group,
            @QueryParam("os") OperatingSystem os);

}

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
    public void create(InstanceGroupConfiguration config);

    @GET
    @Path("/{group}")
    public InstanceGroupConfiguration read(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}")
    public void update(@ActivityScope @PathParam("group") String group, InstanceGroupConfiguration config);

    @DELETE
    @Path("/{group}")
    public void delete(@ActivityScope @PathParam("group") String group);

    @POST
    @Path("/{group}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void updateImage(@ActivityScope @PathParam("group") String group, @FormDataParam("image") InputStream imageData);

    @GET
    @Unsecured // required to allow requests from browser directly (e.g. CSS).
    @Path("/{group}/image")
    @Produces("image/png")
    public InputStream readImage(@ActivityScope @PathParam("group") String group);

    /**
     * Create a new unique (in the given group) UUID for an instance.
     */
    @GET
    @Path("/{group}/new-uuid")
    public String createUuid(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/instance")
    public InstanceResource getInstanceResource(@ActivityScope @PathParam("group") String group);

    @Path("/{group}/product")
    public ProductResource getProductResource(@ActivityScope @PathParam("group") String group);

    @GET
    @Path("/{group}/client-apps")
    public Collection<InstanceClientAppsDto> listClientApps(@ActivityScope @PathParam("group") String group,
            @QueryParam("os") OperatingSystem os);

}

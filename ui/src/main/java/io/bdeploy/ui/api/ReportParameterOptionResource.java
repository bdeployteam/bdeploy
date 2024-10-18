package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.ui.dto.ReportParameterOptionDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ReportParameterOptionResource {

    @GET
    @Path("/instance-purposes")
    public List<ReportParameterOptionDto> getInstancePurposes();

    @GET
    @Path("/instance-groups")
    public List<ReportParameterOptionDto> getInstanceGroups();

    @GET
    @Path("/products")
    public List<ReportParameterOptionDto> getProducts(@QueryParam("instanceGroup") String instanceGroup);

    @GET
    @Path("/product-versions")
    public List<ReportParameterOptionDto> getProductsVersions(@QueryParam("instanceGroup") String instanceGroup,
            @QueryParam("product") String product);

}

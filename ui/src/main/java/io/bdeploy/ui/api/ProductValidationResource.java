package io.bdeploy.ui.api;

import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/product-validation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProductValidationResource {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ProductValidationResponseApi validate(@FormDataParam("file") InputStream inputStream);

}

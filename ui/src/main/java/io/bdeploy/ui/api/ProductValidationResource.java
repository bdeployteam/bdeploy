package io.bdeploy.ui.api;

import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.ui.dto.ProductValidationResponseDto;
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
    public ProductValidationResponseDto validate(@FormDataParam("file") InputStream inputStream);

}

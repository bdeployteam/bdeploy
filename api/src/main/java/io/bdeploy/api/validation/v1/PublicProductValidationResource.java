package io.bdeploy.api.validation.v1;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Public remote API which performs product validation.
 */
@Path("/public/v1/validation")
public interface PublicProductValidationResource {

    /**
     * Receives a ZIP containing all raw product related data. This data is validated and a result is returned. This service
     * allows for validation of raw product data even before any of the applications referenced has been actually built.
     *
     * @param inputStream a ZIP file which contains a {@link ProductValidationDescriptorApi} along with all the referenced files.
     *            Use {@link ProductValidationHelper} instead if possible (i.e. when running on the JVM).
     * @return the validation result.
     */
    @Operation(summary = "Validate raw product data",
               description = "Validates all YAML files involved in a product before actually building the product.")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ProductValidationResponseApi validate(FormDataMultiPart fdmp);

}

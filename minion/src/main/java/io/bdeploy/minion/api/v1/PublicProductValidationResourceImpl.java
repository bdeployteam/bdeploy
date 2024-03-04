package io.bdeploy.minion.api.v1;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.api.validation.v1.PublicProductValidationResource;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.ui.api.impl.ProductValidationResourceImpl;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

public class PublicProductValidationResourceImpl implements PublicProductValidationResource {

    @Context
    private ResourceContext rc;

    @Override
    public ProductValidationResponseApi validate(FormDataMultiPart fdmp) {
        return rc.initResource(new ProductValidationResourceImpl()).validate(fdmp);
    }

}

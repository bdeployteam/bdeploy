package io.bdeploy.minion.api.v1;

import java.io.InputStream;

import io.bdeploy.api.validation.v1.PublicProductValidationResource;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.ui.api.impl.ProductValidationResourceImpl;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

public class PublicProductValidationResourceImpl implements PublicProductValidationResource {

    @Context
    private ResourceContext rc;

    @Override
    public ProductValidationResponseApi validate(InputStream inputStream) {
        return rc.initResource(new ProductValidationResourceImpl()).validate(inputStream);
    }

}

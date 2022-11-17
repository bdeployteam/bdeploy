package io.bdeploy.api.validation.v1.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.api.validation.v1.PublicProductValidationResource;

/**
 * The response object used in {@link PublicProductValidationResource#validate(java.io.InputStream)} to wrap around multiple
 * {@link ProductValidationIssueApi}.
 */
public class ProductValidationResponseApi {

    /**
     * All issues detected during raw product validation.
     */
    public List<ProductValidationIssueApi> issues = new ArrayList<>();

    @JsonCreator
    public ProductValidationResponseApi(@JsonProperty("issues") List<ProductValidationIssueApi> issues) {
        this.issues = issues;
    }

}

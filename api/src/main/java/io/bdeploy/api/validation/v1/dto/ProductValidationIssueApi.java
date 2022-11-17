package io.bdeploy.api.validation.v1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single issue in raw product validation.
 */
public class ProductValidationIssueApi {

    public enum ProductValidationSeverity {
        WARNING,
        ERROR
    }

    /**
     * The severity of the issue detected.
     */
    public ProductValidationIssueApi.ProductValidationSeverity severity;

    /**
     * A human readable message which hints to the issue.
     */
    public String message;

    @JsonCreator
    public ProductValidationIssueApi(@JsonProperty("severity") ProductValidationIssueApi.ProductValidationSeverity severity,
            @JsonProperty("message") String message) {
        this.severity = severity;
        this.message = message;
    }
}
package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProductValidationResponseDto {

    public enum ProductValidationSeverity {
        WARNING,
        ERROR
    }

    public static class ProductValidationIssueDto {

        public ProductValidationSeverity severity;
        public String message;

        @JsonCreator
        public ProductValidationIssueDto(@JsonProperty("severity") ProductValidationSeverity severity,
                @JsonProperty("message") String message) {
            this.severity = severity;
            this.message = message;
        }
    }

    public List<ProductValidationIssueDto> issues = new ArrayList<>();

    public ProductValidationResponseDto() {
    }

    public ProductValidationResponseDto(List<ProductValidationIssueDto> issues) {
        this.issues = issues;
    }

}

package io.bdeploy.ui.dto;

import java.util.List;

public class ProductValidationResponseDto {

    public List<String> errors;

    public ProductValidationResponseDto() {
    }

    public ProductValidationResponseDto(List<String> errors) {
        this.errors = errors;
    }

}

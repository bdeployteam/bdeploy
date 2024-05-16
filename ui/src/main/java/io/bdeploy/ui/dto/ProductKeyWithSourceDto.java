package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.Manifest;

public class ProductKeyWithSourceDto {

    // repository or instance group name of the product
    public String groupOrRepo;

    // product version
    public Manifest.Key key;

    @JsonCreator
    public ProductKeyWithSourceDto(@JsonProperty("groupOrRepo") String groupOrRepo, @JsonProperty("key") Manifest.Key key) {
        this.groupOrRepo = groupOrRepo;
        this.key = key;
    }

}

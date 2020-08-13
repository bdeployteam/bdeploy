package io.bdeploy.api.remote.v1.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import io.bdeploy.bhive.model.Manifest;

public class InstanceConfigurationApi {

    public enum InstancePurposeApi {
        @JsonEnumDefaultValue
        PRODUCTIVE,
        TEST,
        DEVELOPMENT
    }

    /**
     * Globally unique identifier of the instance.
     */
    public String uuid;

    /**
     * Short name of the instance.
     */
    public String name;

    /**
     * Optional Human readable description of the instance.
     */
    public String description;

    /**
     * The intended use of the deployed software.
     */
    public InstancePurposeApi purpose = InstancePurposeApi.DEVELOPMENT;

    /**
     * The key of the product which was used to create the instance.
     */
    public Manifest.Key product;

}

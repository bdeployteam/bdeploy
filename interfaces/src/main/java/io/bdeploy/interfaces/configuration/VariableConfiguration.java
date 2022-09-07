package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;

/**
 * Used as value object for instance and system variables alike.
 */
public class VariableConfiguration {

    public String id;

    public LinkedValueConfiguration value;

    public String description;

    public ParameterType type;

    public String customEditor;

    public VariableConfiguration(String id, String value) {
        this.id = id;
        this.value = new LinkedValueConfiguration(value);
    }

    @JsonCreator
    public VariableConfiguration(@JsonProperty("id") String id, @JsonProperty("value") LinkedValueConfiguration value,
            @JsonProperty("description") String description, @JsonProperty("type") ParameterType type,
            @JsonProperty("customEditor") String customEditor) {
        this.id = id;
        this.value = value;
        this.description = description;
        this.type = type;
        this.customEditor = customEditor;
    }

}

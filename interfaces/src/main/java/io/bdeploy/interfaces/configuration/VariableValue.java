package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;

/**
 * Used as value object for instance and system variables alike.
 */
public class VariableValue {

    public LinkedValueConfiguration value;

    public String description;

    public ParameterType type;

    public String customEditor;

    public VariableValue(String value) {
        this.value = new LinkedValueConfiguration(value);
    }

    @JsonCreator
    public VariableValue(@JsonProperty("value") LinkedValueConfiguration value, @JsonProperty("description") String description,
            @JsonProperty("type") ParameterType type, @JsonProperty("customEditor") String customEditor) {
        this.value = value;
        this.description = description;
        this.type = type;
        this.customEditor = customEditor;
    }

}

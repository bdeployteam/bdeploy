package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;

/**
 * Used as value object for instance and system variables alike.
 */
public class VariableConfiguration {

    @JsonPropertyDescription("The ID of the variable")
    public String id;

    @JsonPropertyDescription("The (initial) value of the variable.")
    public LinkedValueConfiguration value;

    @JsonPropertyDescription("The human readable description/purpose of the variable.")
    public String description;

    @JsonPropertyDescription("The type of the variable. A suitable editor will be provided to edit this variable.")
    public ParameterType type;

    @JsonPropertyDescription("A potential custom editor ID which must be provided by a plugin.")
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

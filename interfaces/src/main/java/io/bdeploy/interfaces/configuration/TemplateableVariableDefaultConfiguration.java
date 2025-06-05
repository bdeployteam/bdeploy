package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

/**
 * Allows overriding the default value of a templated (or any other) instance variable from the instance template.
 */
public class TemplateableVariableDefaultConfiguration {

    @JsonPropertyDescription("The ID of a variable previously created by the template.")
    @JsonProperty(required = true)
    public String id;

    @JsonPropertyDescription("The alternative (initial) value to use instead of the one placed at the original definition of the variable.")
    @JsonProperty(required = true)
    public LinkedValueConfiguration value;
}

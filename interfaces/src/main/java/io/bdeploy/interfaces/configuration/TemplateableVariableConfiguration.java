package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;

public class TemplateableVariableConfiguration extends VariableConfiguration {

    public String template;

    @JsonCreator
    public TemplateableVariableConfiguration(@JsonProperty("template") String template, @JsonProperty("id") String id,
            @JsonProperty("value") LinkedValueConfiguration value, @JsonProperty("description") String description,
            @JsonProperty("type") ParameterType type, @JsonProperty("customEditor") String customEditor) {
        super(id, value, description, type, customEditor);
        this.template = template;
    }

}

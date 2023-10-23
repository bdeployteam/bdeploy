package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor.ParameterType;

public class TemplateableVariableConfiguration extends VariableConfiguration {

    @JsonPropertyDescription("References a variable template definition which must be included in the 'product-info.yaml' from an external file.")
    public String template;

    @JsonCreator
    public TemplateableVariableConfiguration(@JsonProperty("template") String template, @JsonProperty("id") String id,
            @JsonProperty("value") LinkedValueConfiguration value, @JsonProperty("description") String description,
            @JsonProperty("type") ParameterType type, @JsonProperty("customEditor") String customEditor) {
        super(id, value, description, type, customEditor);
        this.template = template;
    }

    public TemplateableVariableConfiguration(TemplateableVariableConfiguration r) {
        this(r.template, r.id, new LinkedValueConfiguration(r.value.getPreRenderable()), r.description, r.type, r.customEditor);
    }

}

package io.bdeploy.interfaces.descriptor.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;

@JsonClassDescription("A template to be included in 'product-info.yaml' which provides shared instance variable definitions.")
public class InstanceVariableTemplateDescriptor {

    @JsonPropertyDescription("The ID of the template. Can be used to reference the template from other variable definitions.")
    @JsonProperty(required = true)
    public String id;

    @JsonPropertyDescription("One or more variable definitions included in this template.")
    @JsonProperty(required = true)
    public List<TemplateableVariableConfiguration> instanceVariables;

}

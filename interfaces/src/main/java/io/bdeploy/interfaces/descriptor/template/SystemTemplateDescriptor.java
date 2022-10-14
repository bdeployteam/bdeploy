package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.VariableConfiguration;

@JsonClassDescription("Defines a template which can be used to create a process systems consisting of many instances.")
public class SystemTemplateDescriptor {

    @JsonPropertyDescription("The human readable name of the template.")
    @JsonProperty(required = true)
    public String name;

    @JsonPropertyDescription("A short but thorough description of what the template will create.")
    public String description;

    @JsonPropertyDescription("A set of system variable definitions included in this template.")
    public List<VariableConfiguration> systemVariables;

    @JsonPropertyDescription("The list of instances to create when this template is applied.")
    public List<SystemTemplateInstanceReference> instances = new ArrayList<>();

}

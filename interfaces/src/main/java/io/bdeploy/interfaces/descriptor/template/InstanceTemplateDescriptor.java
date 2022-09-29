package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;

@JsonClassDescription("Describes a template which can be used to fill an instance with pre-configured processes.")
public class InstanceTemplateDescriptor {

    @JsonPropertyDescription("Short name of the template")
    @JsonProperty(required = true)
    public String name;

    @JsonPropertyDescription("Description of what this template will accomplish to set up.")
    public String description;

    @JsonAlias("variables")
    @JsonPropertyDescription("A list of user-provided variables which can be used in the template. All variables of all application templates used are queried along with these variables when applying the template.")
    public List<TemplateVariable> templateVariables = new ArrayList<>();

    @JsonPropertyDescription("A collection of instance variables to create when applying the instance template.")
    public List<TemplateableVariableConfiguration> instanceVariables = new ArrayList<>();

    @JsonPropertyDescription("A collection of override values for instanceVariables. Especially useful when including instanceVariableTemplates in the product, and referencing them from instanceVariables.")
    public List<TemplateableVariableDefaultConfiguration> instanceVariableDefaults = new ArrayList<>();

    @JsonPropertyDescription("A list of process control groups which should be created when applying the instance template. Each application template can specify the name of a preferred control group it wishes to be put into.")
    public List<InstanceTemplateControlGroup> processControlGroups = new ArrayList<>();

    @JsonPropertyDescription("A list of template groups. The use can select whether to, and where to (node) apply each group.")
    @JsonProperty(required = true)
    public List<InstanceTemplateGroup> groups = new ArrayList<>();

}

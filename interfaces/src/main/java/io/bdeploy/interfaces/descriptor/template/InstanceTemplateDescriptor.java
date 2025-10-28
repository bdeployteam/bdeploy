package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceVariableConfiguration;

@JsonClassDescription("Describes a template which can be used to fill an instance with pre-configured processes.")
public class InstanceTemplateDescriptor {

    @JsonPropertyDescription("Short name of the template")
    @JsonProperty(required = true)
    public String name;

    @JsonPropertyDescription("Description of what this template will accomplish to set up.")
    public String description;

    @JsonPropertyDescription("Whether the instance should be started automatically when starting the minion(s). Will default to false if not specified.")
    public Boolean autoStart;

    @JsonPropertyDescription("Schedule background uninstallation of old instance versions. Will default to true if not specified.")
    public Boolean autoUninstall;

    @JsonAlias("variables")
    @JsonPropertyDescription("A list of user-provided variables which can be used in the template. All variables of all application templates used are queried along with these variables when applying the template.")
    public List<TemplateVariable> templateVariables = new ArrayList<>();

    /**
     * @deprecated As of release 7.2.0
     */
    @Deprecated(since = "7.2.0")
    @JsonPropertyDescription("A collection of instance variables to create when applying the instance template.")
    public List<TemplateableVariableConfiguration> instanceVariables = new ArrayList<>();

    @JsonPropertyDescription("A collection of instance variable values to combine with definitions when applying the instance template.")
    public List<InstanceVariableConfiguration> instanceVariableValues = new ArrayList<>();

    /**
     * @deprecated As of release 7.2.0
     */
    @Deprecated(since = "7.2.0")
    @JsonPropertyDescription("A collection of override values for instanceVariables. Especially useful when including instanceVariableTemplates in the product, and referencing them from instanceVariables.")
    public List<TemplateableVariableDefaultConfiguration> instanceVariableDefaults = new ArrayList<>();

    @JsonPropertyDescription("A list of process control groups which should be created when applying the instance template. Each application template can specify the name of a preferred control group it wishes to be put into.")
    public List<InstanceTemplateControlGroup> processControlGroups = new ArrayList<>();

    @JsonPropertyDescription("A list of template groups. The use can select whether to, and where to (node) apply each group.")
    @JsonProperty(required = true)
    public List<InstanceTemplateGroup> groups = new ArrayList<>();

}

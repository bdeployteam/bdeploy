package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.TemplateableVariableDefaultConfiguration;

/**
 * Describes a template which can be used to fill an instance with pre-configured applications.
 */
public class InstanceTemplateDescriptor {

    /**
     * Short name of the template, e.g. "Simple Configuration"
     */
    public String name;

    /**
     * Description of what this template will accomplish to set up.
     */
    public String description;

    /**
     * A list of user-provided variables which can be used in the template.
     */
    @JsonAlias("variables")
    public List<TemplateVariable> templateVariables = new ArrayList<>();

    /**
     * A collection of variables to create when applying the instance template.
     */
    public List<TemplateableVariableConfiguration> instanceVariables = new ArrayList<>();

    /**
     * A collection of override values for {@link #instanceVariables}.
     */
    public List<TemplateableVariableDefaultConfiguration> instanceVariableDefaults = new ArrayList<>();

    /**
     * A list of process control groups which should be created when applying the instance template.
     * <p>
     * Each template application can specify the name of a control group it wishes to be put into.
     */
    public List<InstanceTemplateControlGroup> processControlGroups = new ArrayList<>();

    /**
     * A list of group definitions
     */
    public List<InstanceTemplateGroup> groups = new ArrayList<>();

}

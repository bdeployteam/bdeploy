package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

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
    public List<InstanceTemplateVariable> variables = new ArrayList<>();

    /**
     * A list of group definitions
     */
    @JsonAlias("nodes") // compat - remove after 2.4.0
    public List<InstanceTemplateGroup> groups = new ArrayList<>();

}

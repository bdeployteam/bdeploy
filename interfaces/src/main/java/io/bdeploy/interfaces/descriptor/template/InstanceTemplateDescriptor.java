package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

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
     * A list of node definitions
     */
    public List<InstanceTemplateNode> nodes = new ArrayList<>();

}

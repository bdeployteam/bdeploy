package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;

public class InstanceTemplateNode {

    /**
     * The name of the node.
     * <p>
     * Note that this is not required to be an actual existing node. The user will have to map "template" nodes to "physical"
     * nodes once the template is applied.
     */
    public String name;

    /**
     * A description of the contents of this node.
     */
    public String description;

    /**
     * The type of applications hosted on this node.
     */
    public ApplicationType type;

    /**
     * The applications which should be configured on this virtual node.
     */
    public List<InstanceTemplateApplication> applications = new ArrayList<>();

}

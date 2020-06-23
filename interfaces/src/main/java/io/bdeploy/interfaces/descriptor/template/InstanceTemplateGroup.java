package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;

public class InstanceTemplateGroup {

    /**
     * The name of the group.
     */
    public String name;

    /**
     * A description of the contents of this group.
     */
    public String description;

    /**
     * The type of applications contained in this group.
     */
    public ApplicationType type;

    /**
     * The applications which should be configured by this group.
     */
    public List<TemplateApplication> applications = new ArrayList<>();

}

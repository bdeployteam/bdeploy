package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;

public class ParameterTemplateDescriptor {

    /**
     * A unique ID which can be used to reference this application template from the instance template.
     */
    public String id;

    /**
     * Local variable declarations.
     */
    public List<ParameterDescriptor> parameters = new ArrayList<>();
}

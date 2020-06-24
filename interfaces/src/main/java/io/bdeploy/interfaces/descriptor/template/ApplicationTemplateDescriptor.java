package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

public class ApplicationTemplateDescriptor extends TemplateApplication {

    /**
     * A unique ID which can be used to reference this application template from the instance template.
     */
    public String id;

    /**
     * Local variable declarations.
     */
    public List<TemplateVariable> variables = new ArrayList<>();
}

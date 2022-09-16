package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ApplicationTemplateDescriptor extends TemplateApplication {

    /**
     * A unique ID which can be used to reference this application template from the instance template.
     */
    public String id;

    /**
     * Local variable declarations.
     */
    @JsonAlias("variables")
    public List<TemplateVariable> templateVariables = new ArrayList<>();
}

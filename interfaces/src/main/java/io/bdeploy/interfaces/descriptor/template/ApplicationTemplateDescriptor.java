package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Defines a template which can be used to create a process configuration for a specific application")
public class ApplicationTemplateDescriptor extends TemplateApplication {

    /**
     * A unique ID which can be used to reference this application template from the instance template.
     */
    @JsonPropertyDescription("The ID of the template. Can be used to reference from other application and instance templates.")
    @JsonProperty(required = true)
    public String id;

    /**
     * Local variable declarations.
     */
    @JsonAlias("variables")
    @JsonPropertyDescription("Definition of variables available in this template.")
    public List<TemplateVariable> templateVariables = new ArrayList<>();

    public ApplicationTemplateDescriptor() {
        // intentionally left blank
    }

    public ApplicationTemplateDescriptor(ApplicationTemplateDescriptor original) {
        super(original);

        this.id = original.id;
        this.templateVariables.addAll(original.templateVariables); // shallow is ok - template variables are immutable.
    }

    @Override
    public ApplicationTemplateDescriptor copy() {
        return new ApplicationTemplateDescriptor(this);
    }
}

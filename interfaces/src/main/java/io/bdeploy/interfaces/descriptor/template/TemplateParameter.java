package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.JsonSchemaAllowedTypes;

public class TemplateParameter {

    @JsonAlias("uid")
    @JsonPropertyDescription("The ID of the referenced parameter definition from the applications app-info.yaml.")
    public String id;

    @JsonPropertyDescription("The value that should be assigned to the parameter. The value can be omitted to add an optional parameter with its default value to the resulting process configuration.")
    @JsonSchemaAllowedTypes({ String.class, Number.class, Boolean.class })
    public String value;

}

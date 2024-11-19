package io.bdeploy.interfaces.descriptor.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.JsonSchemaAllowedTypes;

public class TemplateVariable {

    @JsonAlias("uid")
    @JsonPropertyDescription("The unique ID of the variable. Variables with matching ID are queried from the user only once when applying a template.")
    public String id;

    @JsonPropertyDescription("A short human readable name of the variable.")
    public String name;

    @JsonPropertyDescription("The description which is shown to the user when querying the parameter.")
    public String description;

    @JsonPropertyDescription("Default value as string, can be interpreted as number, etc. depending on the target parameter type.")
    @JsonSchemaAllowedTypes({ String.class, Number.class, Boolean.class })
    public String defaultValue;

    @JsonPropertyDescription("A list of values suggested by the variable input field in the UI.")
    public List<String> suggestedValues;

    @JsonPropertyDescription("The type of the template variable. The template variable value is validated against the type, and proper type-specific editors are provided to users. The default value is STRING.")
    public TemplateVariableType type = TemplateVariableType.STRING;

}

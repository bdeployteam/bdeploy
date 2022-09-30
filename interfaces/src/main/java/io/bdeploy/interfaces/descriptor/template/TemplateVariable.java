package io.bdeploy.interfaces.descriptor.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class TemplateVariable {

    @JsonAlias("uid")
    @JsonPropertyDescription("The unique ID of the variable. Variables with matching ID are queried from the user only once when applying a template.")
    public String id;

    /**
     * @deprecated Compat with 4.x
     */
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    @JsonPropertyDescription("DEPRECATED: Use 'id' instead")
    public String getUid() {
        return id;
    }

    @JsonPropertyDescription("A short human readable name of the variable.")
    public String name;

    @JsonPropertyDescription("The description which is shown to the user when querying the parameter.")
    public String description;

    @JsonPropertyDescription("Default value as string, can be interpreted as number, etc. depending on the target parameter type.")
    public String defaultValue;

    @JsonPropertyDescription("A list of values suggested by the variable input field in the UI.")
    public List<String> suggestedValues;

}

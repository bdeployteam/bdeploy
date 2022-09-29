package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class TemplateParameter {

    @JsonAlias("uid")
    @JsonPropertyDescription("The ID of the referenced parameter definition from the applications app-info.yaml.")
    public String id;

    // Compat with 4.x
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    @JsonPropertyDescription("DEPRECATED: Use 'id' instead")
    public String getUid() {
        return id;
    };

    @JsonPropertyDescription("The value that should be assigned to the parameter. The value can be omitted to add an optional parameter with its default value to the resulting process configuration.")
    public String value;

}

package io.bdeploy.interfaces.descriptor.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class SystemTemplateInstanceTemplateGroupMapping {

    @JsonPropertyDescription("The name of the group in the instance template.")
    @JsonProperty(required = true)
    public String group;

    @JsonPropertyDescription("The name of the node this group shall be applied to by default.")
    @JsonProperty(required = true)
    public String node;

}

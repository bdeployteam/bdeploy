package io.bdeploy.interfaces.descriptor.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A reference to an instance template to create as part of a system template.")
public class SystemTemplateInstanceReference {

    @JsonPropertyDescription("The name of the newly created instance.")
    @JsonProperty(required = true)
    public String name;

    @JsonPropertyDescription("The description of the newly created instance.")
    @JsonProperty(required = true)
    public String description;

    @JsonPropertyDescription("The ID of the product to use. This is the 'product' attribute specified in the product-info.yaml file of the product.")
    @JsonProperty(required = true)
    public String productId;

    @JsonPropertyDescription("An optional regular expression which can be used to narrow down the allowed versions of the specified product to use when applying a system template.")
    public String productVersionRegex = ".*";

    @JsonPropertyDescription("The name of the instance template to use when creating the instance of the product.")
    @JsonProperty(required = true)
    public String templateName;

    @JsonPropertyDescription("A list of proposed default mappings from groups to nodes. If a node does not exist, the mapping is disregarded and the user needs to choose.")
    public List<SystemTemplateInstanceTemplateGroupMapping> defaultMappings;

    @JsonPropertyDescription("A list of fixed values which should be used instead of querying values from the user.")
    public List<TemplateVariableFixedValueOverride> fixedVariables;

}

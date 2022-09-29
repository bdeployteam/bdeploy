package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;

public class InstanceTemplateGroup {

    @JsonPropertyDescription("The name of the group.")
    @JsonProperty(required = true)
    public String name;

    @JsonPropertyDescription("A description of the contents of this group.")
    public String description;

    @JsonPropertyDescription("The type of applications contained in this group. Dictates which target (node, client) can be choosen by the user.")
    public ApplicationType type;

    @JsonPropertyDescription("The applications which should be configured by this group.")
    @JsonProperty(required = true)
    public List<TemplateApplication> applications = new ArrayList<>();

}

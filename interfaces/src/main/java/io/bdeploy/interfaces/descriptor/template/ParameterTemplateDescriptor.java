package io.bdeploy.interfaces.descriptor.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;

@JsonClassDescription("A template to be included in 'product-info.yaml' which provides shared parameter definitions which can be used by all 'app-info.yaml' files in the product.")
public class ParameterTemplateDescriptor {

    @JsonPropertyDescription("A unique ID of the template which can be used to reference the template from an 'app-info.yaml' file.")
    public String id;

    @JsonPropertyDescription("One or more parameter definitions which should be placed inline at the point where this template is referenced.")
    public List<ParameterDescriptor> parameters = new ArrayList<>();
}

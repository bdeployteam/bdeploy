package io.bdeploy.interfaces.descriptor.instance;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;

public class InstanceVariableDefinitionDescriptor {

    @JsonPropertyDescription("Instance variable definitions")
    public List<VariableDescriptor> definitions = new ArrayList<>();
}

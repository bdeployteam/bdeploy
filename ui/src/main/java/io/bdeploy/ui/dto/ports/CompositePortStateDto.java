package io.bdeploy.ui.dto.ports;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;

public class CompositePortStateDto {

    public VariableDescriptor.VariableType type;
    public String paramId;
    public String paramName;
    public int port;

    public List<PortStateDto> states = new ArrayList<>();

    public CompositePortStateDto(VariableDescriptor.VariableType type, String paramId, String paramName, int port) {
        this.type = type;
        this.paramId = paramId;
        this.paramName = paramName;
        this.port = port;
    }

}

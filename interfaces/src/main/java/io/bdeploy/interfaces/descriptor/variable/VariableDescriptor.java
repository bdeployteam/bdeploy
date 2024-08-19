package io.bdeploy.interfaces.descriptor.variable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;

public class VariableDescriptor {

    public enum VariableType {
        @JsonEnumDefaultValue
        STRING,
        NUMERIC,
        BOOLEAN,
        PASSWORD,
        SERVER_PORT,
        CLIENT_PORT,
        URL,
        ENVIRONMENT
    }

    /**
     * A globally unique ID of the variable.
     * For {@link ParameterDescriptor} this is used to identify the "same" parameters on different applications
     * which should be configured globally (see {@link ParameterDescriptor#global}).
     * <p>
     * Also used to ensure ordering of parameters is the same in the {@link ApplicationConfiguration} as defined in the
     * {@link ApplicationDescriptor}.
     */
    @JsonAlias("uid")
    @JsonPropertyDescription("The ID of the variable. This ID must be unique in its scope (application, instance, system).")
    public String id;

    @JsonPropertyDescription("The ID of a variable template registered for the containing product. The therein defined variables will be inlined here. If template is given, no other attribute may be set.")
    public String template;

    @JsonPropertyDescription("The human readable short name of the variable.")
    public String name;

    @JsonAlias("description")
    @JsonPropertyDescription("A human readable description aiding humans in configuring this variable's value.")
    public String longDescription;

    @JsonPropertyDescription("The arbitrary name of a group. Variables with the same groupName are grouped in the UI to help the user in identifying variables that belong together.")
    public String groupName;

    @JsonAlias("value")
    @JsonPropertyDescription("The default value for this variable.")
    public LinkedValueConfiguration defaultValue = null;

    @JsonPropertyDescription("Whether this variable cannot be changed by the user (uses a fixed value). If set to true, a defaultValue must be specified if required. Defaults to 'false'.")
    public boolean fixed = false;

    @JsonPropertyDescription("The type of the variable. The variable value is validated against the type, and proper type-specific editors are provided to users.")
    public VariableType type = VariableType.STRING;

    @JsonPropertyDescription("A list of values suggested to the user when editing the value of this variable.")
    public List<String> suggestedValues = new ArrayList<>();

    @JsonPropertyDescription("The ID of a custom editor which is provided through a BDeploy plugin. If available, this editor will be provided to the user instead of (or in addition to) the default one.")
    public String customEditor;

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VariableDescriptor other = (VariableDescriptor) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
}

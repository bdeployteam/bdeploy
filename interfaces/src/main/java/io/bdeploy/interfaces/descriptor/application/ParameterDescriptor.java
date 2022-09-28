package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

/**
 * Describes a single parameter accepted by an application.
 */
public class ParameterDescriptor implements Comparable<ParameterDescriptor> {

    public enum ParameterType {
        @JsonEnumDefaultValue
        STRING,
        NUMERIC,
        BOOLEAN,
        PASSWORD,
        SERVER_PORT,
        CLIENT_PORT,
        URL
    }

    /**
     * A globally unique ID of the parameter. This is used to identify the "same"
     * parameters on different applications which should be configured globally (see
     * {@link #global}).
     * <p>
     * Also used to assure ordering of parameters is the same in the {@link ApplicationConfiguration} as defined in the
     * {@link ApplicationDescriptor}.
     */
    @JsonAlias("uid")
    @JsonPropertyDescription("The ID of the parameter. This ID must be unique in a given application.")
    public String id;

    // Compat with 4.x
    @Deprecated(forRemoval = true)
    @JsonProperty("uid")
    public String getUid() {
        return id;
    };

    @JsonPropertyDescription("The ID of a parameter template registered for the containing product. The therein defined parameters will be inlined here. If template is given, no other attribute may be set.")
    public String template;

    @JsonPropertyDescription("The human readable short name of the parameter")
    public String name;

    @JsonPropertyDescription("A human readable description aiding humans in configuring this parameter's value.")
    public String longDescription;

    @JsonPropertyDescription("The arbitrary name of a group. Parameters with the same groupName are grouped in the UI to help the user in identifying parameters that belong together.")
    public String groupName;

    @JsonPropertyDescription("The actual parameter as it should be put on the command line of a process, not including the value, e.g. '--myparam'")
    public String parameter;

    @JsonPropertyDescription("Whether this parameter has a value that needs to be configured by the user, defaults to 'true'.")
    public boolean hasValue = true;

    /**
     * If <code>true</code> a {@link #parameter} '--arg' with value 'val' will be
     * rendered as { '--arg', 'val' }. If <code>false</code> the same parameter
     * would be rendered as single argument '--arg=val' (the '=' is configurable).
     */
    @JsonPropertyDescription("Whether the value of the parameter shall be passed as individual argument on the command line of the process, defaults to 'false'.")
    public boolean valueAsSeparateArg = false;

    /**
     * The separator char when {@link #valueAsSeparateArg} is false. Usually '--arg'
     * with value 'val' will be rendered as { '--arg=val' }, using an enmpty String
     * can be used to create arguments like { '--argval' }.
     */
    @JsonPropertyDescription("If valueAsSeparateArg is false (the default), defines the spearator between the parameter and the value, defaults to '='.")
    public String valueSeparator = "=";

    @JsonPropertyDescription("The default value for this parameter")
    public LinkedValueConfiguration defaultValue = null;

    /**
     * Whether the parameter should be configured globally (once per deployment,
     * same value for all applications which have the same parameter) or locally
     * (configure value per application even if the name of the parameter is the
     * same).
     *
     * @deprecated since 4.6.0, replaced by system and instance variables. Note that we need to keep this around for a long time
     *             coming to support existing products. The logic to handle globals is all in the frontend in
     *             process-edit.service.ts.
     */
    @Deprecated(since = "4.6.0")
    @JsonPropertyDescription("DEPRECATED. Whether the parameter is global (i.e. all parameters with the same ID will always have the same value in a single instance). This has been deprecated in favor of instance variables.")
    public boolean global = false;

    @JsonPropertyDescription("Whether this parameter is mandatory. Optional parameters (the default) need to be added by a template or by the user explicitly through the configuration UI. Defaults to 'false'")
    public boolean mandatory = false;

    @JsonPropertyDescription("Whether this parameter cannot be changed by the user (uses a fixed value). If set to true, a defaultValue must be specified if required. Defaults to 'false'.")
    public boolean fixed = false;

    @JsonPropertyDescription("The type of the parameter. The parameter value is validated against the type, and proper type-specific editors are provided to users.")
    public ParameterType type = ParameterType.STRING;

    @JsonPropertyDescription("A list of values suggested to the user when editing the value of this parameter.")
    public List<String> suggestedValues = new ArrayList<>();

    @JsonPropertyDescription("The ID of a custom editor which is provided through a BDeploy Plugin. If available, this editor will be provided to the user instead of (or in addition to) the default one.")
    public String customEditor;

    @JsonPropertyDescription("A condition which must be met for this parameter to be configurable/visible to the user.")
    public ParameterCondition condition;

    @Override
    public int compareTo(ParameterDescriptor o) {
        return id.compareTo(o.id);
    }

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
        ParameterDescriptor other = (ParameterDescriptor) obj;
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

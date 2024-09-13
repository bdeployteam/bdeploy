package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;

/**
 * Describes a single parameter accepted by an application.
 */
public class ParameterDescriptor extends VariableDescriptor implements Comparable<ParameterDescriptor> {

    @JsonPropertyDescription("The ID of a parameter template registered for the containing product. The therein defined parameters will be inlined here. If template is given, no other attribute may be set.")
    public String template;

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

    /**
     * Whether the parameter should be configured globally (once per deployment,
     * same value for all applications which have the same parameter) or locally
     * (configure value per application even if the name of the parameter is the
     * same).
     */
    @JsonPropertyDescription("Whether the parameter is global (i.e. all parameters with the same ID will always have the same value in a single instance). This has been deprecated in favor of instance variables.")
    public boolean global = false;

    @JsonPropertyDescription("Whether this parameter is mandatory. Optional parameters (the default) need to be added by a template or by the user explicitly through the configuration UI. Defaults to 'false'")
    public boolean mandatory = false;

    @JsonPropertyDescription("A condition which must be met for this parameter to be configurable/visible to the user.")
    public ParameterCondition condition;

    @Override
    public int compareTo(ParameterDescriptor o) {
        return id.compareTo(o.id);
    }
}

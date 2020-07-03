package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;

/**
 * Describes a single parameter accepted by an application.
 */
public class ParameterDescriptor implements Comparable<ParameterDescriptor> {

    public enum ParameterType {
        STRING,
        NUMERIC,
        BOOLEAN,
        PASSWORD,
        SERVER_PORT,
        CLIENT_PORT
    }

    /**
     * A globally unique ID of the parameter. This is used to identify the "same"
     * parameters on different applications which should be configured globally (see
     * {@link #global}).
     * <p>
     * Also used to assure ordering of parameters is the same in the {@link ApplicationConfiguration} as defined in the
     * {@link ApplicationDescriptor}.
     */
    public String uid;

    /**
     * Human readable short description of the parameter. This is displayed in
     * configuration UI(s) as input field name to guide the user.
     */
    public String name;

    /**
     * Optional human readable long description of the parameter. This is displayed
     * in the configuration UI(s) as additional hint on demand.
     */
    public String longDescription;

    /**
     * Optional name of a group. This name is used to display parameters with the same
     * group name together in the configuration UI. Parameters with the same group
     * are not required to be adjacent within the descriptor, but can be scattered.
     */
    public String groupName;

    /**
     * The parameter string to use when rendering the parameter. Examples:
     * <ul>
     * <li>-v
     * <li>--verbose
     * <li>-Dproperty
     * <li>--some-arg
     * </ul>
     * Note that no value and no value separator ('=') should be included.
     */
    public String parameter;

    /**
     * Whether the parameter requires a value.
     */
    public boolean hasValue = true;

    /**
     * If <code>true</code> a {@link #parameter} '--arg' with value 'val' will be
     * rendered as { '--arg', 'val' }. If <code>false</code> the same parameter
     * would be rendered as single argument '--arg=val' (the '=' is configurable).
     */
    public boolean valueAsSeparateArg = false;

    /**
     * The separator char when {@link #valueAsSeparateArg} is false. Usually '--arg'
     * with value 'val' will be rendered as { '--arg=val' }, using an enmpty String
     * can be used to create arguments like { '--argval' }.
     */
    public String valueSeparator = "=";

    /**
     * Default value for the parameter. This value is used as template for
     * configuration UI(s).
     */
    public String defaultValue = null;

    /**
     * Whether the parameter should be configured globally (once per deployment,
     * same value for all applications which have the same parameter) or locally
     * (configure value per application even if the name of the parameter is the
     * same).
     */
    public boolean global = false;

    /**
     * Whether the parameter is mandatory. If the parameter is {@link #mandatory}
     * and not configured by the user, the {@link #defaultValue} must be used to
     * render the parameter.
     */
    public boolean mandatory = false;

    /**
     * Whether this parameter defines a non-configurable (fixed) parameter which
     * should be passed to the command as is.
     */
    public boolean fixed = false;

    /**
     * The type of the parameter. Used for validation purposes.
     */
    public ParameterType type = ParameterType.STRING;

    /**
     * Possible values for the parameter
     */
    public List<String> suggestedValues = new ArrayList<>();

    /**
     * If set, try to use a custom editor contributed by a plugin to edit this parameter.
     */
    public String customEditor;

    @Override
    public int compareTo(ParameterDescriptor o) {
        return uid.compareTo(o.uid);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uid == null) ? 0 : uid.hashCode());
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
        if (uid == null) {
            if (other.uid != null) {
                return false;
            }
        } else if (!uid.equals(other.uid)) {
            return false;
        }
        return true;
    }

}

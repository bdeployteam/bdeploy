package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor.VariableType;

/**
 * Describes a single parameter as configured in a configuration UI.
 */
public class ParameterConfiguration {

    public enum ParameterConfigurationTarget {
        COMMAND,
        ENVIRONMENT,
    }

    /**
     * The ID of the parameter, which can be used to reference it from other
     * parameters. This is only stored to allow lookup of parameters.
     */
    @JsonAlias("uid")
    public String id;

    /**
     * Whether this parameter is pinned. This information is used by the web UI to
     * more prominently present those parameters to the user.
     */
    public boolean pinned;

    /**
     * The actual value of the parameter as set in the UI. This field is potentially
     * redundant with an entry in the {@link #preRendered} list. The reason to store
     * it redundantly is solely lookup and expansion of other parameter's
     * references.
     */
    public LinkedValueConfiguration value;

    /**
     * The pre-rendered complete parameter as it should be appended to the command
     * line, but with variables still in place.
     */
    public List<String> preRendered = new ArrayList<>();

    /**
     * Whether the parameter is an environment variable.
     */
    public ParameterConfigurationTarget target = ParameterConfigurationTarget.COMMAND;

    /**
     * Updates the {@link #preRendered} representation of the parameter using the given descriptor (can be <code>null</code> for
     * custom parameters).
     */
    public void preRender(ParameterDescriptor desc) {
        String strValue = value != null && value.getPreRenderable() != null ? value.getPreRenderable() : "";

        if (desc == null) {
            // custom parameter
            preRendered = Collections.singletonList(strValue);
            return;
        }

        if (desc.type == VariableType.ENVIRONMENT) {
            // not on CLI.
            target = ParameterConfigurationTarget.ENVIRONMENT;
            preRendered = List.of(desc.parameter, strValue);
            return;
        }

        if (desc.hasValue) {
            if (desc.valueAsSeparateArg) {
                preRendered = List.of(desc.parameter, strValue);
            } else {
                preRendered = List.of(desc.parameter + desc.valueSeparator + strValue);
            }
        } else {
            preRendered = Collections.singletonList(desc.parameter);
        }
    }

}

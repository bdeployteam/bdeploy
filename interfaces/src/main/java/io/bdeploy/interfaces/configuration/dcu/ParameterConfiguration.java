package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;

/**
 * Describes a single parameter as configured in a configuration UI.
 */
public class ParameterConfiguration {

    /**
     * The ID of the parameter, which can be used to reference it from other
     * parameters. This is only stored to allow lookup of parameters.
     */
    public String uid;

    /**
     * The actual value of the parameter as set in the UI. This field is potentially
     * redundant with an entry in the {@link #preRendered} list. The reason to store
     * it redundantly is solely lookup and expansion of other parameter's
     * references.
     */
    public String value;

    /**
     * The pre-rendered complete parameter as it should be appended to the command
     * line, but with variables still in place.
     */
    public final List<String> preRendered = new ArrayList<>();

    public void preRender(ParameterDescriptor desc) {
        preRendered.clear();

        if (desc.hasValue) {
            if (desc.valueAsSeparateArg) {
                preRendered.add(desc.parameter);
                preRendered.add(value);
            } else {
                preRendered.add(desc.parameter + desc.valueSeparator + value);
            }
        } else {
            preRendered.add(desc.parameter);
        }
    }

}

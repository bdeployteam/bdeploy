package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.bdeploy.common.util.TemplateHelper;

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

    public List<String> renderDescriptor(Function<String, String> valueResolver) {
        return preRendered.stream().map(a -> process(a, valueResolver)).collect(Collectors.toList());
    }

    /**
     * Resolves all variable references in the given String, using the given
     * provider.
     *
     * @param value the raw value, potentially containing variable
     *            references.
     * @param valueProvider the value provider knowing how to resolve variable
     *            references.
     * @return a resolved {@link String}.
     */
    public static String process(String value, Function<String, String> valueProvider) {
        return TemplateHelper.process(value, valueProvider, "{{", "}}");
    }

}

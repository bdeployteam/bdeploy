package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Describes a single parameter as configured in a configuration UI.
 */
public class ParameterConfiguration {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");

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
        if (value == null || !value.contains("${")) {
            return value;
        }

        String processed = value;
        // repeat process as long as the value contains something to expand. This can
        // happen when the value of a variable is another variable.
        while (processed.contains("${")) {
            Matcher m = VAR_PATTERN.matcher(processed);
            StringBuilder builder = new StringBuilder();
            int currentStart = 0;
            while (m.find()) {
                String r = valueProvider.apply(m.group(1));
                if (r == null) {
                    throw new IllegalArgumentException(
                            "Cannot find replacement for variable " + m.group(1) + " while processing " + value);
                }
                // append string from beginning or previous end to start of variable match
                // not using appendReplacement and appendTail since they are slow.
                builder.append(processed.substring(currentStart, m.start()));

                // append substituted variable contents
                builder.append(r);

                // update current "start" position for next find.
                currentStart = m.end();
            }
            builder.append(processed.substring(currentStart));
            processed = builder.toString();
        }

        return processed;
    }

}

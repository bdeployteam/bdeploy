package io.bdeploy.common.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TemplateHelper {

    private static final String PATTERN_START = "{{";
    private static final String PATTERN_END = "}}";
    private static final Pattern PATTERN = Pattern.compile(Pattern.quote(PATTERN_START) + "(.*?)" + Pattern.quote(PATTERN_END));

    private TemplateHelper() {
    }

    /**
     * Resolves all variable references in the given list of strings using the given resolver.
     *
     * @param values
     *            the raw values, potentially containing variable references.
     * @param valueResolver
     *            knows how to replace references with the actual content
     * @return the resolved strings
     */
    public static List<String> process(List<String> values, VariableResolver valueResolver) {
        return process(values, valueResolver, (v) -> true);
    }

    /**
     * Asks the given resolver callback whether or not to resolve the variables contained in the given list of strings. The
     * callback can be used to skip resolving of given variables.
     *
     * @param values
     *            the raw values, potentially containing variable references.
     * @param valueResolver
     *            knows how to replace references with the actual content
     * @param shouldResolve
     *            callback to ask whether or not to resolve the given variable
     * @return the resolved strings
     */
    public static List<String> process(List<String> values, VariableResolver valueResolver, ShouldResolve shouldResolve) {
        return values.stream().map(v -> process(v, valueResolver, shouldResolve)).collect(Collectors.toList());
    }

    /**
     * Resolves all variable references in the given string using the given resolver.
     *
     * @param value
     *            the raw value, potentially containing variable references.
     * @param valueResolver
     *            knows how to replace references with the actual content
     * @return the resolved string
     */
    public static String process(String value, VariableResolver valueResolver) {
        return process(value, valueResolver, (v) -> true);
    }

    /**
     * Resolves all variable references in the given String, using the given resolver. The
     * callback can be used to skip resolving of given variables.
     *
     * @param value
     *            the raw value, potentially containing variable references.
     * @param valueResolver
     *            knows how to replace references with the actual content
     * @param shouldResolve
     *            callback to ask whether or not to resolve the given variable
     * @return the resolved string
     */
    public static String process(String value, VariableResolver valueResolver, ShouldResolve shouldResolve) {
        if (value == null || !value.contains(PATTERN_START)) {
            return value;
        }
        return doProcess(value, valueResolver, shouldResolve);
    }

    /**
     * Recursively resolves the given input.
     */
    private static String doProcess(String value, VariableResolver valueResolver, ShouldResolve shouldResolve) {
        StringBuilder builder = new StringBuilder();
        int currentStart = 0;

        Matcher m = PATTERN.matcher(value);
        while (m.find()) {
            // Resolve next variable if desired
            String nextMatch = m.group(1);
            String resolved = nextMatch;
            if (shouldResolve != null && shouldResolve.apply(nextMatch)) {
                resolved = valueResolver.apply(nextMatch);
                if (resolved == null) {
                    throw new IllegalArgumentException(
                            "Cannot find replacement for variable " + nextMatch + " while processing " + value);
                }
                // Resolve recursive as the replacement can also contains templates
                resolved = doProcess(resolved, valueResolver, shouldResolve);
            } else {
                // Keep pattern for the unresolved intact so that we can resolve it later
                resolved = PATTERN_START + resolved + PATTERN_END;
            }

            // append string from beginning or previous end to start of variable match
            // not using appendReplacement and appendTail since they are slow.
            builder.append(value.substring(currentStart, m.start()));
            builder.append(resolved);

            // Update indices for next round
            currentStart = m.end();
        }

        // Append remaining content of the input
        builder.append(value.substring(currentStart, value.length()));
        return builder.toString();
    }

}

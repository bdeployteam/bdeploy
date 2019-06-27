package io.bdeploy.common.util;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateHelper {

    private TemplateHelper() {
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
    public static String process(String value, UnaryOperator<String> valueProvider, String patternStart, String patternEnd) {
        if (value == null || !value.contains(patternStart)) {
            return value;
        }

        Pattern pattern = Pattern.compile(Pattern.quote(patternStart) + "(.*?)" + Pattern.quote(patternEnd));

        String processed = value;
        // repeat process as long as the value contains something to expand. This can
        // happen when the value of a variable is another variable.
        while (processed.contains(patternStart)) {
            Matcher m = pattern.matcher(processed);
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

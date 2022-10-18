package io.bdeploy.interfaces.configuration.template;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;

public class TrackingTemplateOverrideResolver implements VariableResolver {

    /**
     * this is intentionally not a Variables prefix and PrefixResolver to keep things apart. templates are resolved at a totally
     * different point in time
     */
    private static final String TEMPLATE_PREFIX = "T:";

    private final Set<String> requestedVariables = new TreeSet<>();

    private final List<TemplateVariableFixedValueOverride> overrides;

    public TrackingTemplateOverrideResolver(List<TemplateVariableFixedValueOverride> overrides) {
        this.overrides = overrides;
    }

    public Set<String> getRequestedVariables() {
        return requestedVariables;
    }

    @Override
    public String apply(String t) {
        if (!t.startsWith(TEMPLATE_PREFIX)) {
            return null;
        }

        String expr = t.substring(2); // skip T:
        String varName = expr;

        int colIndex = varName.indexOf(':');
        if (colIndex != -1) {
            varName = varName.substring(0, colIndex);
        }

        String finVarName = varName;
        Optional<TemplateVariableFixedValueOverride> override = overrides.stream().filter(o -> o.id.equals(finVarName))
                .findFirst();
        if (!override.isPresent()) {
            return null;
        }

        if (colIndex != -1) {
            try {
                String op = expr.substring(colIndex + 1);
                Long opNum = Long.parseLong(op);
                Long valNum = Long.parseLong(override.get().value);

                return Long.toString(valNum + opNum);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid variable substitution for " + varName + ": invalid operator or type");
            }
        }

        return override.get().value;
    }

    public boolean canResolve(String t) {
        // leave all non-templates alone
        if (!t.startsWith(TEMPLATE_PREFIX)) {
            return false;
        }

        String expr = t.substring(2); // skip T:
        String varName = expr;

        int colIndex = varName.indexOf(':');
        if (colIndex != -1) {
            varName = varName.substring(0, colIndex);
        }

        String finVarName = varName;
        Optional<TemplateVariableFixedValueOverride> override = overrides.stream().filter(o -> o.id.equals(finVarName))
                .findFirst();

        if (override.isEmpty()) {
            // need to query the user later, no fixed value.
            requestedVariables.add(finVarName);
        }

        return override.isPresent();
    }

}

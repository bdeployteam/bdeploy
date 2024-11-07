package io.bdeploy.interfaces.configuration.template;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.variables.PrefixResolver;
import io.bdeploy.interfaces.variables.Variables;

/**
 * A resolver which can be used primarily for <b>validation</b> purposes.
 * <p>
 * The {@link #canResolve(String)} method will record variables which are requested. This can be used to track down which
 * variables are actually used in a template. This information can later be used to query the user for variable values.
 * <p>
 * This resolver should <b>not</b> be used to perform <b>actual</b> resolution during creation of "thing" from templates, as in
 * that scenario the variables <b>must</b> resolve, and thus tracking of unresolved variables is pointless.
 */
public class TrackingTemplateOverrideResolver extends PrefixResolver {

    private final Set<String> requestedVariables = new TreeSet<>();
    private final List<TemplateVariableFixedValueOverride> overrides;
    private final TrackingTemplateOverrideResolver parent;

    public TrackingTemplateOverrideResolver(List<TemplateVariableFixedValueOverride> overrides) {
        this(overrides, null);
    }

    public TrackingTemplateOverrideResolver(List<TemplateVariableFixedValueOverride> overrides,
            TrackingTemplateOverrideResolver parent) {
        super(Variables.TEMPLATE);
        this.overrides = overrides;
        this.parent = parent;
    }

    @Override
    protected String doResolve(String variable) {
        Optional<TemplateVariableFixedValueOverride> override = overrides.stream().filter(o -> o.id.equals(variable)).findFirst();
        if (override.isPresent()) {
            return override.get().value;
        }
        return parent != null ? parent.doResolve(variable) : null;
    }

    public Set<String> getRequestedVariables() {
        return requestedVariables;
    }

    public boolean canResolve(String t) {
        // leave all non-templates alone
        if (!t.startsWith(Variables.TEMPLATE.getPrefix())) {
            return false;
        }

        String expr = t.substring(Variables.TEMPLATE.getPrefix().length()); // skip T:
        String varName = expr;

        int colIndex = varName.indexOf(':');
        if (colIndex != -1) {
            varName = varName.substring(0, colIndex);
        }

        String finVarName = varName;
        Optional<TemplateVariableFixedValueOverride> override = overrides.stream().filter(o -> o.id.equals(finVarName))
                .findFirst();

        if (override.isEmpty()) {
            boolean parentCanResolve = false;
            if (parent != null) {
                parentCanResolve = parent.canResolve(t);
            }

            if (!parentCanResolve) {
                // need to query the user later, no fixed value.
                requestedVariables.add(finVarName);
            }

            return parentCanResolve;
        }

        return true;
    }
}

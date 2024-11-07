package io.bdeploy.interfaces.configuration.template;

import java.util.List;
import java.util.Optional;

import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.variables.PrefixResolver;
import io.bdeploy.interfaces.variables.Variables;

/**
 * A template variable resolver for actual production use in applying templates
 * <p>
 * As opposed to the {@link TrackingTemplateOverrideResolver} this resolver will resolve from overrides as well as template
 * variable default values. This can be used to put user input values (or template reference/system template provided values) in
 * the overrides, falling back to default values, and worst case not resolving variables (which will cause an error, which is the
 * desired behavior when actually applying a template).
 */
public class TemplateVariableResolver extends PrefixResolver {

    private final List<TemplateVariable> templateVars;
    private final List<TemplateVariableFixedValueOverride> overrides;
    private final TemplateVariableResolver parent;

    public TemplateVariableResolver(List<TemplateVariable> templateVars, List<TemplateVariableFixedValueOverride> overrides,
            TemplateVariableResolver parent) {
        super(Variables.TEMPLATE);
        this.templateVars = templateVars;
        this.overrides = overrides;
        this.parent = parent;
    }

    @Override
    protected String doResolve(String variable) {
        String ovr = doResolveOverrideRecursive(variable);
        if (ovr != null) {
            return ovr;
        }

        return doResolveDefaultRecursive(variable);
    }

    private String doResolveOverrideRecursive(String variable) {
        Optional<TemplateVariableFixedValueOverride> override = overrides.stream().filter(o -> o.id.equals(variable)).findFirst();
        if (override.isPresent()) {
            return override.get().value;
        }

        if (parent != null) {
            return parent.doResolveOverrideRecursive(variable);
        }

        return null;
    }

    private String doResolveDefaultRecursive(String variable) {
        Optional<TemplateVariable> var = templateVars.stream().filter(v -> v.id.equals(variable)).findFirst();
        if (var.isPresent()) {
            return var.get().defaultValue;
        }
        
        if (parent != null) {
            return parent.doResolveDefaultRecursive(variable);
        }

        return null;
    }
}

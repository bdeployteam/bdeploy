package io.bdeploy.interfaces.variables;

import io.bdeploy.common.util.VariableResolver;

/**
 * Resolves variables starting with the DELAY: keyword by delegating the resolving to another resolver. This resolver will
 * always resolve the variable by cutting of the 'DELAY:' prefix and delegate the remaining part to the parent resolver. The
 * actual decision when it is OK to call this resolver - and thus resolve the referenced variable - must be done outside.
 */
public class DelayedVariableResolver extends PrefixResolver {

    private final VariableResolver parentResolver;

    public DelayedVariableResolver(VariableResolver parentResolver) {
        super(Variables.DELAYED);
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        return parentResolver.apply(variable);
    }

}

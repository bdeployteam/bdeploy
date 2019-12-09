package io.bdeploy.interfaces.variables;

import java.util.function.UnaryOperator;

/**
 * Scopes resolving of parameter values to the given application. The actual resolving is done by another resolver.
 */
public class ApplicationParameterValueResolver extends PrefixResolver {

    private final String application;
    private final UnaryOperator<String> parentResolver;

    public ApplicationParameterValueResolver(String application, UnaryOperator<String> parentResolver) {
        super(Variables.PARAMETER_VALUE);
        this.application = application;
        this.parentResolver = parentResolver;
    }

    @Override
    protected String doResolve(String variable) {
        if (!variable.contains(":")) {
            return parentResolver.apply(prefix.format(application + ":" + variable));
        }
        return parentResolver.apply(prefix.format(variable));
    }

}

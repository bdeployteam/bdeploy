package io.bdeploy.interfaces.variables;

/**
 * Resolves parameters of an application contained in the same deployment.
 */
public class ParameterValueResolver extends PrefixResolver {

    private final ApplicationParameterProvider provider;

    public ParameterValueResolver(ApplicationParameterProvider provider) {
        super(Variables.PARAMETER_VALUE);
        this.provider = provider;
    }

    @Override
    protected String doResolve(String variable) {
        return provider.getParameterValue(variable);
    }

}

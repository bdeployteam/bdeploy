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
    protected String doResolve(String nameAndParam) {
        int idx = nameAndParam.lastIndexOf(':');
        if (idx == -1) {
            throw new IllegalArgumentException("Illegal parameter reference. Expecting appName:paramId but got " + nameAndParam);
        }
        String app = nameAndParam.substring(0, idx);
        String parameter = nameAndParam.substring(idx + 1, nameAndParam.length());
        return provider.getValueByDisplayName(app, parameter);
    }

}

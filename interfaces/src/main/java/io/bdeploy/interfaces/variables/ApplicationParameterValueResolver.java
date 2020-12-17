package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * Scopes resolving of parameter values to the given application. The actual resolving is done by another resolver.
 */
public class ApplicationParameterValueResolver extends PrefixResolver {

    private final String appUid;
    private final ApplicationParameterProvider provider;

    public ApplicationParameterValueResolver(String appUid, InstanceNodeConfiguration nodeConfig) {
        super(Variables.PARAMETER_VALUE);
        this.provider = new ApplicationParameterProvider(nodeConfig);
        this.appUid = appUid;
    }

    @Override
    protected String doResolve(String variable) {
        // Variable contains a reference to another application. Delegate resolving
        if (variable.contains(":")) {
            return null;
        }
        return provider.getValueById(appUid, variable);
    }

}

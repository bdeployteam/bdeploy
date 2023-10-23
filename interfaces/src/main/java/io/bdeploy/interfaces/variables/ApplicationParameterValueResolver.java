package io.bdeploy.interfaces.variables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * Scopes resolving of parameter values to the given application. The actual resolving is done by another resolver.
 */
public class ApplicationParameterValueResolver extends PrefixResolver {

    private static final Logger log = LoggerFactory.getLogger(ApplicationParameterValueResolver.class);
    private final String appId;
    private final ApplicationParameterProvider provider;

    public ApplicationParameterValueResolver(String appId, InstanceNodeConfiguration nodeConfig) {
        super(Variables.PARAMETER_VALUE);
        this.provider = new ApplicationParameterProvider(nodeConfig);
        this.appId = appId;
    }

    @Override
    protected String doResolve(String variable) {
        // Variable contains a reference to another application. Delegate resolving
        if (variable.contains(":")) {
            return null;
        }
        try {
            return provider.getValueById(appId, variable);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot resolve " + variable + " on " + appId, e);
            }
            return null;
        }
    }

}

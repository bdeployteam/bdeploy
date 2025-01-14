package io.bdeploy.interfaces.variables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves parameters of an application contained in the same deployment.
 */
public class ParameterValueResolver extends PrefixResolver {

    private static final Logger log = LoggerFactory.getLogger(ParameterValueResolver.class);
    private final ApplicationParameterProvider provider;

    public ParameterValueResolver(ApplicationParameterProvider provider) {
        super(Variables.PARAMETER_VALUE);
        this.provider = provider;
    }

    @Override
    protected String doResolve(String nameAndParam) {
        try {
            int idx = nameAndParam.lastIndexOf(':');
            if (idx == -1) {
                throw new IllegalArgumentException(
                        "Illegal parameter reference. Expecting appName:paramId but got " + nameAndParam);
            }
            String app = nameAndParam.substring(0, idx);
            String parameter = nameAndParam.substring(idx + 1);
            return provider.getValueByDisplayName(app, parameter);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot resolve " + nameAndParam, e);
            }
            return null;
        }
    }

}

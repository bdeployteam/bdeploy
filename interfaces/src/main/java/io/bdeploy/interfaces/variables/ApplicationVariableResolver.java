package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;

public class ApplicationVariableResolver extends PrefixResolver {

    private final ApplicationConfiguration appConfig;

    public ApplicationVariableResolver(ApplicationConfiguration appConfig) {
        super(Variables.APP_VALUE);
        this.appConfig = appConfig;
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
            case "UUID":
                return appConfig.uid;
            case "NAME":
                return appConfig.name;
            default:
                return null;
        }
    }

}

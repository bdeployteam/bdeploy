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
        return switch (variable) {
            case "ID" -> appConfig.id;
            case "UUID" -> appConfig.id; // deprecated/old
            case "NAME" -> appConfig.name;
            default -> null;
        };
    }
}

package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;

public class ApplicationVariableResolver extends PrefixResolver {

    private final String id;
    private final String name;

    public ApplicationVariableResolver(ApplicationConfiguration appConfig) {
        super(Variables.APP_VALUE);
        this.id = appConfig.id;
        this.name = appConfig.name;
    }

    public ApplicationVariableResolver(ProcessConfiguration process) {
        super(Variables.APP_VALUE);
        this.id = process.id;
        this.name = process.name;
    }

    @Override
    protected String doResolve(String variable) {
        return switch (variable) {
            case "ID" -> id;
            case "UUID" -> id; // deprecated/old
            case "NAME" -> name;
            default -> null;
        };
    }
}

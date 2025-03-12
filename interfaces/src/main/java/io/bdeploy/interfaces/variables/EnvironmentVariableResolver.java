package io.bdeploy.interfaces.variables;

/**
 * Returns the value of the specified environment variable.
 */
public class EnvironmentVariableResolver extends PrefixResolver {

    public EnvironmentVariableResolver() {
        super(Variables.ENVIRONMENT_VARIABLE);
    }

    @Override
    protected String doResolve(String variable) {
        String env = System.getenv(variable);
        return env != null ? env : "";
    }
}

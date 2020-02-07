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
        String value = System.getenv(variable);
        if (value == null) {
            throw new IllegalStateException("Environment variable " + variable + " not set.");
        }
        return value;
    }

}

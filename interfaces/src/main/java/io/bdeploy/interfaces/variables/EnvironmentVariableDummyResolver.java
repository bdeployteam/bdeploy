package io.bdeploy.interfaces.variables;

public class EnvironmentVariableDummyResolver extends EnvironmentVariableResolver {

    @Override
    protected String doResolve(String variable) {
        // just *any* value which is not an expression for validation.
        return "<" + variable + ">";
    }

}

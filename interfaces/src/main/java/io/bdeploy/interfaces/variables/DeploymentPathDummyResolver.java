package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

public class DeploymentPathDummyResolver extends PrefixResolver {

    public DeploymentPathDummyResolver() {
        super(Variables.DEPLOYMENT_PATH);
    }

    @Override
    protected String doResolve(String variable) {
        // just *any* value which is not an expression for validation.
        return "<" + SpecialDirectory.valueOf(variable).getDirName() + ">";
    }

}

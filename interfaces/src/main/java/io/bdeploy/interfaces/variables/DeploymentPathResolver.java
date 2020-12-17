package io.bdeploy.interfaces.variables;

import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * Resolves a deployment path.
 */
public class DeploymentPathResolver extends PrefixResolver {

    private final DeploymentPathProvider provider;

    public DeploymentPathResolver(DeploymentPathProvider provider) {
        super(Variables.DEPLOYMENT_PATH);
        this.provider = provider;
    }

    @Override
    protected String doResolve(String variable) {
        return provider.get(SpecialDirectory.valueOf(variable)).toString();
    }

}

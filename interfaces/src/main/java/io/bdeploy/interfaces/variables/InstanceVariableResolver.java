package io.bdeploy.interfaces.variables;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A variable resolver capable to resolve instance specific variables.
 */
public class InstanceVariableResolver extends PrefixResolver {

    private final InstanceNodeConfiguration incf;
    private final DeploymentPathProvider paths;

    public InstanceVariableResolver(InstanceNodeConfiguration incf, DeploymentPathProvider paths) {
        super(Variables.INSTANCE_VALUE);
        this.incf = incf;
        this.paths = paths;
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
            case "SYSTEM_PURPOSE":
                return incf.purpose == null ? "" : incf.purpose.name();
            case "UUID":
                return incf.uuid;
            case "NAME":
                return incf.name;
            case "PRODUCT_ID":
                return incf.product == null ? "" : incf.product.getName();
            case "PRODUCT_TAG":
                return incf.product == null ? "" : incf.product.getTag();
            case "DEPLOYMENT_INFO_FILE":
                return paths.get(SpecialDirectory.ROOT).resolve(InstanceDeploymentInformationApi.FILE_NAME).toAbsolutePath()
                        .toString();
            default:
                return null;
        }
    }

}

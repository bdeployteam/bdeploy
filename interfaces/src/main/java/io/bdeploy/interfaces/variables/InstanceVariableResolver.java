package io.bdeploy.interfaces.variables;

import java.nio.file.Path;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * A variable resolver capable to resolve instance specific variables.
 */
public class InstanceVariableResolver extends PrefixResolver {

    private final InstanceNodeConfiguration incf;
    private final Path appDir;
    private final String tag;

    public InstanceVariableResolver(InstanceNodeConfiguration incf, Path appDir, String tag) {
        super(Variables.INSTANCE_VALUE);
        this.incf = incf;
        this.appDir = appDir;
        this.tag = tag;
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
            case "SYSTEM_PURPOSE":
                return incf.purpose == null ? "" : incf.purpose.name();
            case "ID":
                return incf.id;
            case "UUID": // deprecated/old
                return incf.id;
            case "TAG":
                return tag;
            case "NAME":
                return incf.name;
            case "PRODUCT_ID":
                return incf.product == null ? "" : incf.product.getName();
            case "PRODUCT_TAG":
                return incf.product == null ? "" : incf.product.getTag();
            case "DEPLOYMENT_INFO_FILE":
                if (appDir == null) {
                    return InstanceDeploymentInformationApi.FILE_NAME; // used during validation
                }
                return appDir.resolve(InstanceDeploymentInformationApi.FILE_NAME).normalize().toAbsolutePath().toString();
            default:
                return null;
        }
    }
}

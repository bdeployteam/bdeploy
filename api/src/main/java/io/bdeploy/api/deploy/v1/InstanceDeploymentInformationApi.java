package io.bdeploy.api.deploy.v1;

import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;

/**
 * Describes a deployments current state. This object is persisted to a file. To retrieve the path to the file in an app-info.yaml
 * use the <code>{{I:DEPLOYMENT_INFO_FILE}}</code> expansion.
 * <p>
 * The file containing this object is updated whenever BDeploy updates the deployment of this instance. Thus it can be consumed by
 * applications to detect changes to the current deployment states.
 * <p>
 * This file is available for server applications only. The client launcher does not provide this file.
 */
public class InstanceDeploymentInformationApi {

    public static final String FILE_NAME = "deployment-info.json";

    /**
     * The currently active instance version.
     */
    public String activeInstanceVersion;

    /**
     * The instance this information refers to. The information refers to the currently active instance version.
     * <p>
     * Note that the description field is currently always empty in this scenario.
     */
    public InstanceConfigurationApi instance;

}

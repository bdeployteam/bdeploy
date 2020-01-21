/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import org.eclipse.tea.core.services.TaskingConfigurationExtension;
import org.eclipse.tea.core.services.TaskingConfigurationExtension.TaskingConfig;
import org.osgi.service.component.annotations.Component;

@Component
@TaskingConfig(description = "BDeploy Configuration")
public class BDeployConfig implements TaskingConfigurationExtension {

    @TaskingConfigProperty(description = "Absolute path to product descriptor", headlessOnly = true)
    public String bdeployProductFile;

    @TaskingConfigProperty(description = "Key of the product to push", headlessOnly = true)
    public String bdeployProductPushKey;

    @TaskingConfigProperty(description = "URI of target server to push to", headlessOnly = true)
    public String bdeployProductPushServer;

    @TaskingConfigProperty(description = "Token of target server to push to", headlessOnly = true)
    public String bdeployProductPushToken;

    @TaskingConfigProperty(description = "Instance Group on target server to push to", headlessOnly = true)
    public String bdeployProductPushGroup;

    @TaskingConfigProperty(description = "Path to the product list file, may contain string substitutions.")
    public String bdeployProductListFile;

    @TaskingConfigProperty(description = "Clear BHive on build")
    public boolean clearBHive = false;

    @TaskingConfigProperty(description = "BDeploy Software Repositories")
    public String bdeployServer; // default none

    @TaskingConfigProperty(description = "BDeploy Software Repositories Server Token")
    public String bdeployServerToken; // default none

}

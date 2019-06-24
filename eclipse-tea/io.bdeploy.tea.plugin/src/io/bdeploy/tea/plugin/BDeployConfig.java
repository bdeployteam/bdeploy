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

    @TaskingConfigProperty(description = "Path to the product list file, may contain string substitutions.")
    public String bdeployProductListFile;

    @TaskingConfigProperty(description = "Clear BHive on build")
    public boolean clearBHive = false;

    @TaskingConfigProperty(description = "BDeploy Server")
    public String bdeployServer; // default none

    @TaskingConfigProperty(description = "BDeploy Server Token")
    public String bdeployServerToken; // default none

    @TaskingConfigProperty(description = "Instance Group to push result to")
    public String bdeployTargetInstanceGroup; // default none

}

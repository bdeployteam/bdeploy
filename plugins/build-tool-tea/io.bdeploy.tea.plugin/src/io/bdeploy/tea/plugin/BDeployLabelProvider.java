/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.util.Map;

import io.bdeploy.tea.plugin.services.BDeployProductBuild;

public interface BDeployLabelProvider {

    public Map<String, String> getLabels(BDeployProductBuild build);

}

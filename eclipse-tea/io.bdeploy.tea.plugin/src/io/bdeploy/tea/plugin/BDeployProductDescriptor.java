/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.tea.plugin.services.BDeployApplicationDescriptor;

/**
 * Represents a product build description.
 */
public class BDeployProductDescriptor {

    public String productInfoYaml;

    public List<BDeployApplicationDescriptor> applications = new ArrayList<>();

}

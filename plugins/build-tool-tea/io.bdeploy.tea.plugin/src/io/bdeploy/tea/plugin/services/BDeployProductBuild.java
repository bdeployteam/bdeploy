/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin.services;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Holds information about the currently ongoing product build */
public class BDeployProductBuild {

    public Path productInfo;
    public String productTag;
    public List<BDeployApplicationBuild> apps = new ArrayList<>();

}
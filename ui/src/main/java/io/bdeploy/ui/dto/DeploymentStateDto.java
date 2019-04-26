package io.bdeploy.ui.dto;

import java.util.Set;
import java.util.TreeSet;

public class DeploymentStateDto {

    public String activatedVersion;
    public Set<String> deployedVersions = new TreeSet<>();
    public Set<String> offlineMasterVersions = new TreeSet<>();

}

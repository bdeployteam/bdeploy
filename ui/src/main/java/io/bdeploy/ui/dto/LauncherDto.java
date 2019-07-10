package io.bdeploy.ui.dto;

import java.util.HashMap;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * A DTO containing the latest launcher for each operating system
 */
public class LauncherDto {

    public Map<OperatingSystem, Manifest.Key> launchers = new HashMap<>();

}

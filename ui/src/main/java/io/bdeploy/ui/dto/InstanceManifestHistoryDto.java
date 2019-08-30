package io.bdeploy.ui.dto;

/**
 * Holds brief information about the history of a single instance version.
 */
public class InstanceManifestHistoryDto {

    public long createdAt;
    public long lastInstall;
    public long lastUninstall;
    public long lastActivate;
    public long lastDeactivate;

}

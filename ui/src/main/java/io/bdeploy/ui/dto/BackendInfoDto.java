package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.Version;
import io.bdeploy.ui.api.MinionMode;

/**
 * Basic information about the running server.
 */
public class BackendInfoDto {

    public Version version;
    public MinionMode mode;
    public long time;
    public String name;
    public boolean isNewGitHubReleaseAvailable;
    public boolean isInitialConnectionCheckFailed;

    @JsonCreator
    public BackendInfoDto(@JsonProperty("version") Version version, @JsonProperty("mode") MinionMode mode,
            @JsonProperty("name") String name, @JsonProperty("isNewGitHubReleaseAvailable") boolean isNewGitHubReleaseAvailable,
            @JsonProperty("isInitialConnectionCheckFailed") boolean isInitialConnectionCheckFailed) {
        this.version = version;
        this.mode = mode;
        this.time = System.currentTimeMillis();
        this.name = name;
        this.isNewGitHubReleaseAvailable = isNewGitHubReleaseAvailable;
        this.isInitialConnectionCheckFailed = isInitialConnectionCheckFailed;
    }

}

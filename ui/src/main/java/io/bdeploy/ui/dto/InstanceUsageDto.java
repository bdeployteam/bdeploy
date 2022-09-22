package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InstanceUsageDto {

    @JsonAlias("uuid")
    public String id;

    // Compat with 4.x
    @Deprecated(forRemoval = true)
    @JsonProperty("uuid")
    public String getUuid() {
        return id;
    };

    public String tag;
    public String name;
    public String description;

}

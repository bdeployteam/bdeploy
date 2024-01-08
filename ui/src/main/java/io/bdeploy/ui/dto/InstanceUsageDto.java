package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class InstanceUsageDto {

    @JsonAlias("uuid")
    public String id;

    public String tag;
    public String name;
    public String description;

}

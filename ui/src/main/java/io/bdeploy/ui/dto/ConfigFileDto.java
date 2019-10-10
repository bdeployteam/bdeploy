package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigFileDto {

    /**
     * The relative path of the file.
     */
    public String path;

    /**
     * Whether this file is a text file (and can be edited online).
     */
    public boolean isText;

    @JsonCreator
    public ConfigFileDto(@JsonProperty("path") String path, @JsonProperty("isText") boolean isText) {
        this.path = path;
        this.isText = isText;

    }

}

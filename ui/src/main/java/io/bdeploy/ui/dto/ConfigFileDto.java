package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.ObjectId;

public class ConfigFileDto {

    /**
     * The relative path of the file.
     */
    public String path;

    /**
     * Whether this file is a text file (and can be edited online).
     */
    public boolean isText;

    /** The id of the file as it is stored in the instance. Null for files which are in the product but not in the instance. */
    public ObjectId instanceId;

    /** The id of the file as provided by the product. Null for files which are custom, or no longer provided */
    public ObjectId productId;

    @JsonCreator
    public ConfigFileDto(@JsonProperty("path") String path, @JsonProperty("isText") boolean isText,
            @JsonProperty("instanceId") ObjectId instanceId, @JsonProperty("productId") ObjectId productId) {
        this.path = path;
        this.isText = isText;
        this.instanceId = instanceId;
        this.productId = productId;
    }

}

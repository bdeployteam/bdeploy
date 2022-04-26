package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bundles instance group configuration with additional info
 */
public class InstanceGroupConfigurationDto {

    public InstanceGroupConfiguration instanceGroupConfiguration;

    /**
     * Extra information to be used in global search UI
     */
    public String searchableText;

    @JsonCreator
    public InstanceGroupConfigurationDto(@JsonProperty("instanceGroupConfiguration") InstanceGroupConfiguration igc,
            @JsonProperty("searchableText") String searchableText) {
        this.instanceGroupConfiguration = igc;
        this.searchableText = searchableText;
    }

}

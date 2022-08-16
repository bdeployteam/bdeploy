package io.bdeploy.interfaces.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Used as value object for instance and system variables alike.
 */
public class VariableValue {

    public String value;

    public String description;

    public VariableValue(String value) {
        this.value = value;
    }

    @JsonCreator
    public VariableValue(@JsonProperty("value") String value, @JsonProperty("description") String description) {
        this.value = value;
        this.description = description;
    }

}

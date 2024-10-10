package io.bdeploy.interfaces.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportParameterDescriptor {

    public String key;

    public String label;

    public String description;

    public ReportParameterType type;

    public boolean required = false;

    public String dependsOn;

    public ReportParameterDescriptor(String key, String label, String description, ReportParameterType type, boolean required) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.type = type;
        this.required = required;
    }

    @JsonCreator
    public ReportParameterDescriptor(@JsonProperty("key") String key, @JsonProperty("label") String label,
            @JsonProperty("description") String description, @JsonProperty("type") ReportParameterType type,
            @JsonProperty("required") boolean required, @JsonProperty("dependsOn") String dependsOn) {
        this(key, label, description, type, required);
        this.dependsOn = dependsOn;
    }
}

package io.bdeploy.interfaces.report;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportParameterDescriptor {

    /**
     * key to fetch parameter value from request
     */
    public final String key;

    /**
     * human readable name for the parameter
     */
    public final String label;

    /**
     * description, displayed as tooltip on UI form
     */
    public final String description;

    /**
     * UI form input type
     */
    public final ReportParameterInputType inputType;

    /**
     * list of parameter keys which trigger refreshing options/suggestions for this parameter
     */
    public final List<String> dependsOn;

    /**
     * path in ReportParameterOptionResource to fetch options/suggestions from
     */
    public final String parameterOptionsPath;

    @JsonCreator
    public ReportParameterDescriptor(@JsonProperty("key") String key, @JsonProperty("label") String label,
            @JsonProperty("description") String description, @JsonProperty("inputType") ReportParameterInputType inputType,
            @JsonProperty("dependsOn") List<String> dependsOn,
            @JsonProperty("parameterOptionsPath") String parameterOptionsPath) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.inputType = inputType;
        this.dependsOn = dependsOn;
        this.parameterOptionsPath = parameterOptionsPath;
    }

    public ReportParameterDescriptor(String key, String label, String description, ReportParameterInputType inputType) {
        this(key, label, description, inputType, Collections.emptyList(), null);
    }
}

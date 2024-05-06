package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InstanceTemplateReferenceResultDto {

    public enum InstanceTemplateReferenceStatus {
        OK,
        WARNING,
        ERROR
    }

    public String name;

    public InstanceTemplateReferenceStatus status;

    public String message;

    @JsonCreator
    public InstanceTemplateReferenceResultDto(@JsonProperty("name") String name,
            @JsonProperty("status") InstanceTemplateReferenceStatus status, @JsonProperty("message") String message) {
        this.name = name;
        this.status = status;
        this.message = message;
    }
}

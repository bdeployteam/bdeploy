package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemTemplateResultDto {

    public List<SystemTemplateInstanceResultDto> results = new ArrayList<>();

    public enum SystemTemplateInstanceStatus {
        OK,
        WARNING,
        ERROR
    }

    public static class SystemTemplateInstanceResultDto {

        public String name;

        public SystemTemplateInstanceStatus status;

        public String message;

        @JsonCreator
        public SystemTemplateInstanceResultDto(@JsonProperty("name") String name,
                @JsonProperty("status") SystemTemplateInstanceStatus status, @JsonProperty("message") String message) {
            this.name = name;
            this.status = status;
            this.message = message;
        }
    }

}

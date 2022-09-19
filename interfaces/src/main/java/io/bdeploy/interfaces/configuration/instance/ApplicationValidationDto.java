package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ApplicationValidationDto {

    @JsonAlias("appUid")
    public String appId;
    @JsonAlias("paramUid")
    public String paramId;
    public String message;

    @JsonCreator
    public ApplicationValidationDto(@JsonProperty("appId") String appId, @JsonProperty("paramId") String paramId,
            @JsonProperty("message") String message) {
        this.appId = appId;
        this.paramId = paramId;
        this.message = message;
    }

}

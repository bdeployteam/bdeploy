package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ApplicationValidationDto {

    public String appUid;
    public String paramUid;
    public String message;

    @JsonCreator
    public ApplicationValidationDto(@JsonProperty("appUid") String appUid, @JsonProperty("paramUid") String paramUid,
            @JsonProperty("message") String message) {
        this.appUid = appUid;
        this.paramUid = paramUid;
        this.message = message;
    }

}

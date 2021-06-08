package io.bdeploy.interfaces.configuration.instance;

public class ApplicationValidationDto {

    public String appUid;
    public String paramUid;
    public String message;

    public ApplicationValidationDto(String appUid, String paramUid, String message) {
        this.appUid = appUid;
        this.paramUid = paramUid;
        this.message = message;
    }

}

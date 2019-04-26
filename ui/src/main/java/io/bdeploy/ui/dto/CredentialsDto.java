package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CredentialsDto {

    public final String user;
    public final String password;

    @JsonCreator
    public CredentialsDto(@JsonProperty("user") String user, @JsonProperty("password") String password) {
        this.user = user;
        this.password = password;
    }
}
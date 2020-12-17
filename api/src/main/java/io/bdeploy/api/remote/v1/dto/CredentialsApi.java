package io.bdeploy.api.remote.v1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CredentialsApi {

    public final String user;
    public final String password;

    @JsonCreator
    public CredentialsApi(@JsonProperty("user") String user, @JsonProperty("password") String password) {
        this.user = user;
        this.password = password;
    }
}

package io.bdeploy.interfaces;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information required for updating the current local user's password
 */
public class UserChangePasswordDto implements Comparable<UserChangePasswordDto> {

    public final String user;
    public String currentPassword;
    public String newPassword;

    @JsonCreator
    public UserChangePasswordDto(@JsonProperty("user") String user, @JsonProperty("currentPassword") String currentPassword,
            @JsonProperty("newPassword") String newPassword) {
        this.user = user;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    @Override
    public int compareTo(UserChangePasswordDto o) {
        return user.compareTo(o.user);
    }
}
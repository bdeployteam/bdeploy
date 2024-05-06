package io.bdeploy.interfaces;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Generated;

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

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        UserChangePasswordDto other = (UserChangePasswordDto) obj;
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }
        return true;
    }
}

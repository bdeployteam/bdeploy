package io.bdeploy.minion.user;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;

public interface Authenticator {

    /**
     * @param user the user information
     * @return whether the authenticator is repsonsible for (and can authenticate) this user
     */
    boolean isResponsible(UserInfo user, AuthenticationSettingsDto settings);

    /**
     * @param username the username
     * @param password the password
     * @param trace collector for tracing information
     * @return a {@link UserInfo} object prefilled with required information for persisting if this {@link Authenticator} supports
     *         automatic creation of authenticated users.
     */
    UserInfo getInitialInfo(String username, char[] password, AuthenticationSettingsDto settings, AuthTrace trace);

    /**
     * @param user the user to authenticate
     * @param password the password given by the user.
     * @param trace collector for tracing information
     * @return a user if authentication was successfull. The object may contain updated user information.
     */
    UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace);

}

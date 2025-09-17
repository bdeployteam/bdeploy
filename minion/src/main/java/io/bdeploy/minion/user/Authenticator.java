package io.bdeploy.minion.user;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.AuthenticationSettingsDto;

public interface Authenticator {

    /**
     * @param user the user information
     * @return whether the authenticator is responsible for (and can authenticate) this user
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
     * @return a user if authentication was successful. The object may contain updated user information.
     */
    UserInfo authenticate(UserInfo user, char[] password, AuthenticationSettingsDto settings, AuthTrace trace);

    /**
     * @param user the user to validate. the user presented a valid BDeploy internal token already.
     * @return <code>true</code> in case the existing authentication is still valid, <code>false</code> otherwise.
     */
    boolean isAuthenticationValid(UserInfo user, AuthenticationSettingsDto settings);

}

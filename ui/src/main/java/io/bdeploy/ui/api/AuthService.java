package io.bdeploy.ui.api;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.UserPermissionUpdateDto;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;

public interface AuthService {

    /**
     * @param user the user to verify
     * @param pw the password to verify
     * @param authenticator optional specific authenticator(s) to use.
     * @return the {@link UserInfo} for this user if authenticated, <code>null</code> otherwise.
     */
    public UserInfo authenticate(String user, String pw, SpecialAuthenticators... authenticator);

    /**
     * @param user the user to trace
     * @param pw the password or empty
     * @return List of tracing messages.
     */
    public List<String> traceAuthentication(String user, String pw);

    /**
     * @param dto the LDAP settings
     * @return "OK" or error output
     */
    public String testLdapServer(LDAPSettingsDto dto);

    /**
     * @param dto the LDAP settings
     * @return import result or error output
     */
    public String importAccountsLdapServer(LDAPSettingsDto dto);

    /**
     * @param info the updated user information.
     */
    public void updateUserInfo(UserInfo info);

    /**
     * Updates the ScopedPermissions for a list of users on a single instance group or software repository.
     *
     * @param target the name of the instance group or software repository.
     * @param permissions list of user with granted Permission.
     */
    public void updatePermissions(String target, UserPermissionUpdateDto[] permissions);

    /**
     * Removes the ScopedCapabilities for all users on a single instance group or software repository.
     *
     * @param target the name of the instance group or software repository.
     */
    public void removePermissions(String target);

    /**
     * @param user the user to create
     * @param pw the password to assign initially
     * @param permissions the initial permissions of the user.
     */
    public void createLocalUser(String user, String pw, Collection<ScopedPermission> permissions);

    /**
     * @param user the user to update
     * @param pw the new password to hash and store.
     */
    public void updateLocalPassword(String user, String pw);

    /**
     * Deletes the given user, regardless of whether it is local or externally managed.
     *
     * @param name the name of the user.
     * @return <code>true</code> if a user with the specified name existed, else <code>false</code>
     */
    public boolean deleteUser(String name);

    /**
     * Lookup the given user's information.
     *
     * @param name the name of the user
     * @return all known information for the user.
     */
    public UserInfo getUser(String name);

    /**
     * @return all users known to the system without loading the full user information
     */
    public SortedSet<String> getAllNames();

    /**
     * @return all users known to the system with full user information
     */
    public SortedSet<UserInfo> getAll();

    /**
     * @param user the user's name
     * @param required the required permission
     * @return whether the user with the given name has the given permission.
     */
    public boolean isAuthorized(String user, ScopedPermission required);

    /**
     * @param groupId the ID of the user group
     * @param user the name of the user
     */
    public void addUserToGroup(String groupId, String user);

    /**
     * @param group group's id
     * @param user user's name
     */
    public void removeUserFromGroup(String group, String user);
}

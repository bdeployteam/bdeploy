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
     * @param name the name of the user to verify
     * @param password the password to verify
     * @param authenticator optional specific authenticator(s) to use
     * @return the {@link UserInfo} for this user if authenticated, <code>null</code> otherwise
     */
    public UserInfo authenticate(String name, String password, SpecialAuthenticators... authenticator);

    /**
     * @param name the name of the user to trace
     * @param password the password or empty
     * @return {@link List} of tracing messages
     */
    public List<String> traceAuthentication(String name, String password);

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
     * Updates the information of the given user. Cannot be used to modify the password - use
     * {@link #updateLocalPassword(String, String)} for that purpose.
     * <p>
     * Does not support updating external users.
     * <p>
     * Will not remove global administrator rights if there is no other active global administrator.
     *
     * @param info the updated user information
     * @see #updateLocalPassword(String, String)
     */
    public void updateUserInfo(UserInfo info);

    /**
     * Updates the {@link ScopedPermission ScopedPermissions} for an array of users within the given scope.
     * <p>
     * Does not work for the global scope.
     *
     * @param scope the scope (name of the instance group or software repository)
     * @param permissions array of user with granted {@link ScopedPermission#permission}
     */
    public void updatePermissions(String scope, UserPermissionUpdateDto[] permissions);

    /**
     * Removes all {@link ScopedPermission permissions} for all users within the given scope.
     * <p>
     * Does not work for the global scope.
     *
     * @param scope the scope (name of the instance group or software repository)
     */
    public void removePermissions(String scope);

    /**
     * Creates a local user.
     *
     * @param name the unique name of the user to create
     * @param password the password to assign initially
     * @param permissions the initial permissions of the user
     */
    public void createLocalUser(String name, String password, Collection<ScopedPermission> permissions);

    /**
     * Updates the password of a local user.
     *
     * @param name the user whose password to update
     * @param password the new password to hash and store
     */
    public void updateLocalPassword(String name, String password);

    /**
     * Deletes the user with the given name. Never deletes the last active global administrator.
     *
     * @param name the name of the user to delete
     */
    public void deleteUser(String name);

    /**
     * Lookup the given user's information.
     *
     * @param name the name of the user
     * @return a deep copy of all known information about the user
     */
    public UserInfo getUser(String name);

    /**
     * @return all users known to the system without loading the full user information
     * @see #getAll()
     */
    public SortedSet<String> getAllNames();

    /**
     * @return all users known to the system with full user information
     * @see #getAllNames()
     */
    public SortedSet<UserInfo> getAll();

    /**
     * @param name the name of the user
     * @param required the required permission
     * @return <code>true</code> if the user with the given name has the given permission, else <code>false</code>
     */
    public boolean isAuthorized(String name, ScopedPermission required);

    /**
     * Adds a user to a group.
     *
     * @param groupId the ID of the user group
     * @param name the name of the user
     */
    public void addUserToGroup(String groupId, String name);

    /**
     * Removes a user from a group.
     *
     * @param groupId the ID of the user group
     * @param name the name of the user
     */
    public void removeUserFromGroup(String groupId, String name);
}

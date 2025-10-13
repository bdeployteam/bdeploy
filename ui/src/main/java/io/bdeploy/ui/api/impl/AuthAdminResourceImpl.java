package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.UserBulkResource;
import io.bdeploy.ui.api.UserGroupBulkResource;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

public class AuthAdminResourceImpl implements AuthAdminResource {

    @Inject
    private AuthService auth;

    @Inject
    private AuthGroupService authGroup;

    @Inject
    private ChangeEventManager cem;

    @Inject
    private ActionFactory af;

    @Context
    private ResourceContext rc;

    @Override
    public void createLocalUser(UserInfo info) {
        auth.createLocalUser(info.name, info.password, info.permissions);
        auth.updateUserInfo(info);
        cem.create(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, info.name));
    }

    @Override
    public void updateLocalUserPassword(String user, String password) {
        auth.updateLocalPassword(user, password);
    }

    @Override
    public void updateUser(UserInfo info) {
        auth.updateUserInfo(info);
        cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, info.name));
    }

    @Override
    public void updateUsers(List<UserInfo> infos) {
        infos.forEach(this::updateUser);
        infos.forEach(u -> cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, u.name)));
    }

    @Override
    public boolean deleteUser(String name) {
        boolean result = auth.deleteUser(name);
        if (result) {
            cem.remove(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
        }
        return result;
    }

    @Override
    public UserInfo getUser(String name) {
        return auth.getUser(name);
    }

    @Override
    public SortedSet<String> getAllUserNames() {
        return auth.getAllNames();
    }

    @Override
    public SortedSet<UserInfo> getAllUser() {
        return auth.getAll();
    }

    @Override
    public String createId() {
        return UuidHelper.randomId();
    }

    @Override
    public List<String> traceAuthentication(CredentialsApi credentials) {
        return auth.traceAuthentication(credentials.user, credentials.password);
    }

    @Override
    public String testLdapServer(LDAPSettingsDto dto) {
        return auth.testLdapServer(dto);
    }

    @Override
    public String importAccountsLdapServer(LDAPSettingsDto dto) {
        try (ActionHandle h = af.run(Actions.LDAP_SYNC, null, null, dto.id)) {
            String feedback = auth.importAccountsLdapServer(dto);
            cem.change(ObjectChangeType.USER, Collections.emptyMap());
            cem.change(ObjectChangeType.USER_GROUP, Collections.emptyMap());
            return feedback;
        }
    }

    @Override
    public void addUserToGroup(String group, String user) {
        auth.addUserToGroup(group, user);
        cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, user));

    }

    @Override
    public void removeUserFromGroup(String group, String user) {
        auth.removeUserFromGroup(group, user);
        cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, user));
    }

    @Override
    public SortedSet<UserGroupInfo> getAllUserGroups() {
        return authGroup.getAll();
    }

    @Override
    public void createUserGroup(UserGroupInfo info) {
        authGroup.createUserGroup(info);
        cem.create(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, info.id));
    }

    @Override
    public void updateUserGroup(UserGroupInfo info) {
        authGroup.updateUserGroup(info);
        cem.change(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, info.id));
    }

    @Override
    public void deleteUserGroups(String group) {
        authGroup.deleteUserGroup(group);
        cem.remove(ObjectChangeType.USER_GROUP, Collections.singletonMap(ObjectChangeDetails.USER_GROUP_ID, group));
    }

    @Override
    public UserBulkResource getUserBulkResource() {
        return rc.initResource(new UserBulkResourceImpl());
    }

    @Override
    public UserGroupBulkResource getUserGroupBulkResource() {
        return rc.initResource(new UserGroupBulkResourceImpl());
    }
}

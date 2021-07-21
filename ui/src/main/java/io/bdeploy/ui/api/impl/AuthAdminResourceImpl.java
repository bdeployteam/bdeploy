package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.LDAPSettingsDto;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;

public class AuthAdminResourceImpl implements AuthAdminResource {

    @Inject
    private AuthService auth;

    @Inject
    private ChangeEventManager cem;

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
        infos.forEach(u -> {
            cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, u.name));
        });
    }

    @Override
    public void deleteUser(String name) {
        auth.deleteUser(name);
        cem.remove(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, name));
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
    public String createUuid() {
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
}

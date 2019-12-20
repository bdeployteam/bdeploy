package io.bdeploy.ui.api.impl;

import java.util.List;
import java.util.SortedSet;

import javax.inject.Inject;

import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthService;

public class AuthAdminResourceImpl implements AuthAdminResource {

    @Inject
    private AuthService auth;

    @Override
    public void createLocalUser(UserInfo info) {
        auth.createLocalUser(info.name, info.password, info.capabilities);
        auth.updateUserInfo(info);
    }

    @Override
    public void updateLocalUserPassword(String user, String password) {
        auth.updateLocalPassword(user, password);
    }

    @Override
    public void updateUser(UserInfo info) {
        auth.updateUserInfo(info);
    }

    @Override
    public void updateUsers(List<UserInfo> infos) {
        infos.forEach(this::updateUser);
    }

    @Override
    public void deleteUser(String userName) {
        auth.deleteUser(userName);
    }

    @Override
    public UserInfo getUser(String name) {
        return auth.getUser(name);
    }

    @Override
    public SortedSet<String> getAllUserNames() {
        return auth.getAllNames();
    }

}

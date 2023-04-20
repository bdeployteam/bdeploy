package io.bdeploy.ui.api.impl;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import io.bdeploy.api.remote.v1.dto.CredentialsApi;
import io.bdeploy.interfaces.UserChangePasswordDto;
import io.bdeploy.interfaces.UserGroupInfo;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.settings.SpecialAuthenticators;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthGroupService;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class AuthResourceImpl implements AuthResource {

    @Inject
    private AuthService auth;

    @Inject
    private AuthGroupService authGroup;

    @Inject
    private SecurityContext context;

    @Inject
    private Minion minion;

    @Inject
    private ChangeEventManager cem;

    @Context
    private ResourceContext rc;

    @Override
    public Response authenticate(CredentialsApi credentials, SpecialAuthenticators auth) {
        String token = doAuthenticate(credentials, false, auth);
        // cookie not set to 'secure' to allow sending during development.
        // cookie header set manually, as the NewCookie API does not support SameSite policies.
        return Response.ok().header("Set-Cookie", "st=" + token + ";Version=1;Path=/;Max-Age=365;SameSite=Strict").entity(token)
                .build();
    }

    @Override
    public Response authenticatePacked(CredentialsApi credentials) {
        String tokenPack = doAuthenticate(credentials, true);
        return Response.ok().entity(tokenPack).build();
    }

    private String doAuthenticate(CredentialsApi cred, boolean pack, SpecialAuthenticators... auths) {
        UserInfo info = auth.authenticate(cred.user, cred.password, auths);
        if (info != null) {
            trimUserGroups(info);
            UserInfo clone = authGroup.getCloneWithMergedPermissions(info);
            return minion.createToken(cred.user, clone.getGlobalPermissions(), pack);
        } else {
            throw new WebApplicationException("Invalid credentials", Status.UNAUTHORIZED);
        }
    }

    private void trimUserGroups(UserInfo info) {
        Set<UserGroupInfo> groups = authGroup.getUserGroups(info.groups);
        // if user holds ids of deleted groups update user with trimmed group ids
        if (info.groups.size() != groups.size()) {
            info.groups = groups.stream().map(g -> g.id).collect(Collectors.toSet());
            auth.updateUserInfo(info);
        }
    }

    @Override
    public UserInfo getCurrentUser() {
        UserInfo info = auth.getUser(context.getUserPrincipal().getName());
        if (info == null) {
            return null;
        }
        // dumb deep clone by JSON round-trip here - otherwise we update the cached in memory object.
        UserInfo clone = authGroup.getCloneWithMergedPermissions(info);
        clone.password = null;
        return clone;
    }

    @Override
    public void updateCurrentUser(UserInfo info) {
        auth.updateUserInfo(info);
        cem.change(ObjectChangeType.USER, Collections.singletonMap(ObjectChangeDetails.USER_NAME, info.name));
    }

    @Override
    public Response changePassword(UserChangePasswordDto dto) {
        if (auth.authenticate(dto.user, dto.currentPassword) != null) {
            auth.updateLocalPassword(dto.user, dto.newPassword);
            return Response.ok().build();
        } else {
            throw new WebApplicationException("Invalid credentials", Status.UNAUTHORIZED);
        }
    }

    @Override
    public String getAuthPack(String user, Boolean full) {
        if (user == null) {
            user = context.getUserPrincipal().getName();
        }

        UserInfo userInfo = auth.getUser(user);
        UserInfo clone = authGroup.getCloneWithMergedPermissions(userInfo);
        return minion.createToken(user, clone.getGlobalPermissions(), Boolean.TRUE.equals(full));
    }

    @Override
    public AuthAdminResource getAdmin() {
        return rc.initResource(new AuthAdminResourceImpl());
    }

}

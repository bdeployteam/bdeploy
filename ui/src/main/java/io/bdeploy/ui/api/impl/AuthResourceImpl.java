package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.interfaces.UserChangePasswordDto;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.CredentialsDto;

public class AuthResourceImpl implements AuthResource {

    @Inject
    @Named(JerseyServer.TOKEN_SIGNER)
    private Function<ApiAccessToken, String> signer;

    @Inject
    private AuthService auth;

    @Inject
    private SecurityContext context;

    @Inject
    private Minion minion;

    @Context
    private ResourceContext rc;

    @Override
    public Response authenticate(CredentialsDto credentials) {
        String token = doAuthenticate(credentials, false);
        // cookie not set to 'secure' to allow sending during development.
        // cookie header set manually, as the NewCookie API does not support SameSite policies.
        return Response.ok().header("Set-Cookie", "st=" + token + ";Version=1;Path=/;Max-Age=365;SameSite=Strict").entity(token)
                .build();
    }

    @Override
    public Response authenticatePacked(CredentialsDto credentials) {
        String tokenPack = doAuthenticate(credentials, true);
        return Response.ok().entity(tokenPack).build();
    }

    private String doAuthenticate(CredentialsDto cred, boolean pack) {
        UserInfo info = auth.authenticate(cred.user, cred.password);
        if (info != null) {
            if (pack) {
                return minion.createToken(cred.user, info.permissions);
            }

            ApiAccessToken.Builder token = new ApiAccessToken.Builder().setIssuedTo(cred.user);

            // apply global permissions. scoped ones are not in the token.
            info.permissions.stream().filter(c -> c.scope == null).forEach(token::addPermission);
            String st = signer.apply(token.build());

            return st;
        } else {
            throw new WebApplicationException("Invalid credentials", Status.UNAUTHORIZED);
        }
    }

    @Override
    public List<String> getRecentlyUsedInstanceGroups() {
        String user = context.getUserPrincipal().getName();
        List<String> reversed = new ArrayList<>(auth.getRecentlyUsedInstanceGroups(user));
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public UserInfo getCurrentUser() {
        UserInfo info = auth.getUser(context.getUserPrincipal().getName());

        if (info == null) {
            return null;
        }

        // dumb deep clone by JSON round-trip here - otherwise we update the cached in memory object.
        UserInfo clone = StorageHelper.fromRawBytes(StorageHelper.toRawBytes(info), UserInfo.class);
        clone.password = null;
        return clone;
    }

    @Override
    public void updateCurrentUser(UserInfo info) {
        auth.updateUserInfo(info);
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
    public String getAuthPack(String user) {
        if (user == null) {
            user = context.getUserPrincipal().getName();
        }
        UserInfo userInfo = auth.getUser(user);
        return minion.createToken(user, userInfo.getGlobalPermissions());
    }

    @Override
    public AuthAdminResource getAdmin() {
        return rc.initResource(new AuthAdminResourceImpl());
    }

}

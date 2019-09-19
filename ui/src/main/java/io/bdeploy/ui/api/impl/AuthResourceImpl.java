package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.AuthService.UserInfo;
import io.bdeploy.ui.dto.CredentialsDto;

public class AuthResourceImpl implements AuthResource {

    @Inject
    @Named(JerseyServer.TOKEN_SIGNER)
    private Function<ApiAccessToken, String> signer;

    @Inject
    private AuthService auth;

    @Inject
    private SecurityContext context;

    @Override
    public Response authenticate(CredentialsDto cred) {
        UserInfo info = auth.authenticate(cred.user, cred.password);
        if (info != null) {
            ApiAccessToken.Builder token = new ApiAccessToken.Builder().setIssuedTo(cred.user);
            info.capabilities.forEach(token::addCapability);
            String st = signer.apply(token.build());

            // cookie not set to 'secure' to allow sending during development.
            return Response.ok().cookie(new NewCookie("st", st, "/", null, null, 365, false)).entity(st).build();
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

}

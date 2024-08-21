package io.bdeploy.jersey.resources;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

public class RedirectOnApiRootAccessImpl implements RedirectOnApiRootAccess {

    @Context
    UriInfo uri;

    @Override
    public Response redirectMe() {
        // go one up, which is skipping /api
        return Response.temporaryRedirect(this.uri.getBaseUri().resolve("..")).build();
    }

}

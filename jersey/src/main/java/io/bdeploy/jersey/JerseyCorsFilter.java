package io.bdeploy.jersey;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Allow CORS for testing.
 * <p>
 * Credits go to https://stackoverflow.com/a/28067653/1651581
 */
@Provider
@PreMatching
public class JerseyCorsFilter implements ContainerResponseFilter, ContainerRequestFilter {

    /**
     * Method for ContainerRequestFilter.
     */
    @Override
    public void filter(ContainerRequestContext request) throws IOException {

        // If it's a preflight request, we abort the request with
        // a 200 status, and the CORS headers are added in the
        // response filter method below.
        if (isPreflightRequest(request)) {
            request.abortWith(Response.ok().build());
            return;
        }
    }

    /**
     * A preflight request is an OPTIONS request with an Origin header.
     */
    static boolean isPreflightRequest(ContainerRequestContext request) {
        return request.getHeaderString("Origin") != null && request.getMethod().equalsIgnoreCase("OPTIONS");
    }

    /**
     * Method for ContainerResponseFilter.
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        String origin = request.getHeaderString("Origin");

        // if there is no Origin header, then it is not a
        // cross origin request. We don't do anything.
        if (origin == null) {
            return;
        }

        if (origin.contains("localhost")) {
            // If it is a preflight request, then we add all the CORS headers here.
            if (isPreflightRequest(request)) {
                response.getHeaders().add("Access-Control-Allow-Credentials", "true");
                response.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
                response.getHeaders().add("Access-Control-Allow-Headers",
                        // Whatever other non-standard/safe headers
                        // you want the client to be able to send to the server,
                        // put it in this list. And remove the ones you don't want.
                        "X-Requested-With, Authorization, Accept-Version, Content-Type, Content-MD5, CSRF-Token, "
                                + "X-No-Global-Error-Handling, " // dummy header to supress error handling in web-app.
                                + JerseySseActivityProxyClientFilter.PROXY_SCOPE_HEADER);
            }

            // Cross origin requests can be either simple requests
            // or preflight request. We need to add this header
            // to both type of requests. Only preflight requests
            // need the previously added headers.
            response.getHeaders().add("Access-Control-Allow-Origin", origin);
        }
    }

}

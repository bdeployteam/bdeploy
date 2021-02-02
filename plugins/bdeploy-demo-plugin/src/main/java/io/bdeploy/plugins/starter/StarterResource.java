package io.bdeploy.plugins.starter;

import java.util.Base64;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

/**
 * This simple resource has the capability to Base64 encode and decode a string.
 * <p>
 * The starter frontend module uses this APIs to demonstrate an editor with backend data binding.
 */
@Path("/starter")
public class StarterResource {

    @GET
    @Path("/encode")
    public String enc(@QueryParam("v") String value) {
    	return Base64.getEncoder().encodeToString(value.getBytes());
    }

    @GET
    @Path("/decode")
    public String dec(@QueryParam("v") String value) {
        return new String(Base64.getDecoder().decode(value));
    }
}

package io.bdeploy.plugins.starter;

import java.util.Base64;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

/**
 * This simple resource has the capability to Base64 encode and decode a string.
 * <p>
 * The starter frontend module uses this APIs to demonstrate an editor with backend data binding.
 */
@javax.ws.rs.Path("/starter")
public class StarterResource {

    @GET
    @javax.ws.rs.Path("/encode")
    public String enc(@QueryParam("v") String value) {
    	return Base64.getEncoder().encodeToString(value.getBytes());
    }

    @GET
    @javax.ws.rs.Path("/decode")
    public String dec(@QueryParam("v") String value) {
        return new String(Base64.getDecoder().decode(value));
    }
}

package io.bdeploy.minion.endpoints;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;

@Path("/test/with")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HelloEndpoint {

    @Context
    private HttpHeaders hdrs;

    @Path("/path")
    @GET
    @Unsecured
    public HelloResult getHello() {
        HelloResult result = new HelloResult();
        result.hello = "world";
        result.time = System.currentTimeMillis();
        return result;
    }

    @Path("/path")
    @PUT
    @Unsecured
    public HelloResult putHello(@QueryParam("value") String value, HelloResult input) {
        HelloResult result = new HelloResult();
        result.hello = input.hello + " - " + value + " - " + hdrs.getHeaderString("TestHeader");
        result.time = System.currentTimeMillis();
        return result;
    }

    public static class HelloResult {

        public String hello;
        public long time;
    }

}

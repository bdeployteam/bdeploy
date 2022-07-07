package io.bdeploy.minion.endpoints;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

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

    @Path("/path/sub")
    @GET
    @Unsecured
    public HelloResult subHello() {
        HelloResult result = new HelloResult();
        result.hello = "sub";
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

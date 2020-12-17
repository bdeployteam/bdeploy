package io.bdeploy.jersey.locking;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Singleton
@Path("/locking")
public interface LockedResource {

    @GET
    public String getValue();

    @POST
    public void setValue(String value);

    @POST
    @Path("/unlocked")
    public void setValueUnlocked(String value);

}

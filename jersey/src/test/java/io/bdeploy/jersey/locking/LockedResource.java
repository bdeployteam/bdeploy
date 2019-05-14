package io.bdeploy.jersey.locking;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

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

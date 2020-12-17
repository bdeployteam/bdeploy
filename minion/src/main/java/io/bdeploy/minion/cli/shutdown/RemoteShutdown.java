package io.bdeploy.minion.cli.shutdown;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/shutdown")
public interface RemoteShutdown {

    @POST
    public void shutdown(String token);

}

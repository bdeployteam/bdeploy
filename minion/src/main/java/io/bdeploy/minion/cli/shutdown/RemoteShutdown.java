package io.bdeploy.minion.cli.shutdown;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("/shutdown")
public interface RemoteShutdown {

    @POST
    public void shutdown(String token);

}

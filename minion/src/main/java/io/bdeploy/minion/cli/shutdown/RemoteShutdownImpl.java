package io.bdeploy.minion.cli.shutdown;

import io.bdeploy.common.util.Threads;
import io.bdeploy.jersey.JerseyServer;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class RemoteShutdownImpl implements RemoteShutdown {

    private final JerseyServer server;
    private final String token;

    public RemoteShutdownImpl(JerseyServer server, String token) {
        this.server = server;
        this.token = token;
    }

    @Override
    public void shutdown(String token) {
        if (!this.token.equals(token)) {
            throw new WebApplicationException("Shutdown token mismatch", Status.FORBIDDEN);
        }

        // async to give time to return the call.
        new Thread(() -> {
            Threads.sleep(200);
            server.close();
        }, "Shutdown Initiator").start();
    }

}

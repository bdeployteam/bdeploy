package io.bdeploy.minion.cli.shutdown;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.jersey.JerseyServer;

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
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // intentionally not handling, we want the close to proceed normally.
            }
            server.close();
        }, "Shutdown Initiator").start();
    }

}

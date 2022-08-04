package io.bdeploy.pcu;

import java.util.concurrent.atomic.AtomicBoolean;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpEndpointType;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.jersey.TestServer;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

@Path("/startup")
public class StartupProbe {

    public static final int STATUS_NOT_STARTED = 588;

    @Inject
    @Named("startupFinished")
    AtomicBoolean startupFinished;

    @GET
    @Unsecured
    @Produces(MediaType.TEXT_PLAIN)
    public String getStartup() {
        if (!startupFinished.get()) {
            throw new WebApplicationException("Still Starting", STATUS_NOT_STARTED);
        }
        return "Started";
    }

    static HttpEndpoint createEndpoint(TestServer server) {
        HttpEndpoint probe = new HttpEndpoint();
        probe.id = "startup";
        probe.type = HttpEndpointType.PROBE_STARTUP;
        probe.path = "/api/startup";
        probe.port = new LinkedValueConfiguration(Integer.toString(server.getPort()));
        probe.secure = new LinkedValueConfiguration("true");
        probe.trustAll = true;
        return probe;
    }

}

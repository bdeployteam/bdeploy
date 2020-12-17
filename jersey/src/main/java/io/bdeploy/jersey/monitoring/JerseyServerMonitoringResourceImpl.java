package io.bdeploy.jersey.monitoring;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;

@Path("/server-monitor")
@Produces(MediaType.APPLICATION_JSON)
public class JerseyServerMonitoringResourceImpl {

    @Inject
    private JerseyServerMonitoringSamplerService jsmss;

    @GET
    @RequiredPermission(permission = Permission.ADMIN)
    public JerseyServerMonitoringDto getMonitoringSamples() {
        return jsmss.getSamples();
    }

}

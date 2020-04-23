package io.bdeploy.jersey.monitoring;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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

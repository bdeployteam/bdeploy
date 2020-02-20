/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.e4.core.di.annotations.Execute;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;

public class BDeployCheckServerOnlineTask {

    @Execute
    public void check(BDeployConfig cfg) {
        if (cfg.bdeployServer != null) {
            if (cfg.bdeployServerToken == null) {
                throw new IllegalStateException("Invalid config - target server configured, but token not");
            }

            RemoteService svc = new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);
            PublicRootResource master = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);

            // any actual remote call to verify the connection.
            master.getSoftwareRepositories();
        }
    }

}

/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.e4.core.di.annotations.Execute;

import io.bdeploy.api.remote.v1.PublicRootResource;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;

public class BDeployCheckServerOnlineTask {

    private final BDeployTargetSpec target;
    private final BDeployTargetSpec source;

    public BDeployCheckServerOnlineTask(BDeployTargetSpec target, BDeployTargetSpec source) {
        this.target = target;
        this.source = source;
    }

    @Execute
    public void check(BDeployConfig cfg) {
        if (cfg.bdeployServer != null) {
            if (cfg.bdeployServerToken == null && source == null) {
                throw new IllegalStateException("Invalid config - source server configured, but token not or not logged in");
            }

            RemoteService svc;
            if (source != null) {
                svc = new RemoteService(UriBuilder.fromUri(source.uri).build(), source.token);
            } else {
                svc = new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);
            }
            PublicRootResource master = JerseyClientFactory.get(svc).getProxyClient(PublicRootResource.class);

            // any actual remote call to verify the connection.
            master.getSoftwareRepositories();
        }

        if (target != null) {
            PublicRootResource master = JerseyClientFactory
                    .get(new RemoteService(UriBuilder.fromUri(target.uri).build(), target.token))
                    .getProxyClient(PublicRootResource.class);

            // any actual remote call to verify the connection.
            master.getSoftwareRepositories();
        }
    }

}

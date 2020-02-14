package io.bdeploy.ui.api.impl;

import javax.inject.Singleton;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.ws.BroadcastingAuthenticatedWebSocket;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;

public class UiResources {

    private UiResources() {
    }

    public static void register(RegistrationTarget server) {
        server.registerRoot(new CLStaticHttpHandler(UiResources.class.getClassLoader(), "/webapp/"));
        server.register(AuthResourceImpl.class);
        server.register(HiveResourceImpl.class);
        server.register(BackendInfoResourceImpl.class);

        server.register(InstanceGroupResourceImpl.class);
        server.register(SoftwareRepositoryResourceImpl.class);

        server.register(SoftwareUpdateResourceImpl.class);
        server.register(CleanupResourceImpl.class);
        server.register(DownloadServiceImpl.class);

        server.register(ManagedServersResourceImpl.class);
        server.register(ManagedServersAttachEventResourceImpl.class);

        server.register(CapabilityRequestFilter.class);

        BroadcastingAuthenticatedWebSocket instanceUpdateBc = new BroadcastingAuthenticatedWebSocket(StorageHelper::toRawBytes,
                server.getKeyStore());
        BroadcastingAuthenticatedWebSocket attachEventBc = new BroadcastingAuthenticatedWebSocket(o -> o.toString().getBytes(),
                server.getKeyStore());

        server.registerWebsocketApplication("/instance-updates", instanceUpdateBc);
        server.registerWebsocketApplication("/attach-events", attachEventBc);

        server.register(new AbstractBinder() {

            @Override
            protected void configure() {
                bind(DownloadTokenCache.class).in(Singleton.class).to(DownloadTokenCache.class);
                bind(ProductTransferService.class).in(Singleton.class).to(ProductTransferService.class);
                bind(InstanceEventManager.class).in(Singleton.class).to(InstanceEventManager.class);

                bind(instanceUpdateBc).named(InstanceEventManager.INSTANCE_BROADCASTER).to(JerseyEventBroadcaster.class);
                bind(attachEventBc).named(ManagedServersAttachEventResource.ATTACH_BROADCASTER).to(JerseyEventBroadcaster.class);
            }
        });
    }

}

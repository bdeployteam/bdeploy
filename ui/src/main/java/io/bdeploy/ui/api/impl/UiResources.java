package io.bdeploy.ui.api.impl;

import jakarta.inject.Singleton;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.ws.BroadcastingAuthenticatedWebSocket;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.ui.ProductDiscUsageService;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.RemoteEntryStreamRequestService;

public class UiResources {

    private UiResources() {
    }

    public static void register(RegistrationTarget server) {
        server.addHandler(new CLStaticHttpHandler(UiResources.class.getClassLoader(), "/webapp/"), HttpHandlerRegistration.ROOT);
        server.register(AuthResourceImpl.class);
        server.register(AuditResourceImpl.class);
        server.register(HiveResourceImpl.class);
        server.register(BackendInfoResourceImpl.class);

        server.register(InstanceGroupResourceImpl.class);
        server.register(SoftwareRepositoryResourceImpl.class);

        server.register(SoftwareUpdateResourceImpl.class);
        server.register(CleanupResourceImpl.class);
        server.register(DownloadServiceImpl.class);

        server.register(ManagedServersResourceImpl.class);
        server.register(ManagedServersAttachEventResourceImpl.class);

        server.register(PluginResourceImpl.class);
        server.register(LoggingAdminResourceImpl.class);

        server.register(PermissionRequestFilter.class);

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
                bind(RemoteEntryStreamRequestService.class).in(Singleton.class).to(RemoteEntryStreamRequestService.class);
                bind(ProductDiscUsageService.class).in(Singleton.class).to(ProductDiscUsageService.class);

                bind(instanceUpdateBc).named(InstanceEventManager.INSTANCE_BROADCASTER).to(JerseyEventBroadcaster.class);
                bind(attachEventBc).named(ManagedServersAttachEventResourceImpl.ATTACH_BROADCASTER)
                        .to(JerseyEventBroadcaster.class);
            }
        });
    }

}

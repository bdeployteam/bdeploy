package io.bdeploy.ui.api.impl;

import javax.inject.Singleton;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.ui.api.MasterProvider;

public class UiResources {

    private static InstanceEventManager instanceEvents = new InstanceEventManager();

    private UiResources() {
    }

    public static InstanceEventManager getInstanceEventManager() {
        return instanceEvents;
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

        server.register(InstanceEventBroadcaster.class);

        server.register(new AbstractBinder() {

            @Override
            protected void configure() {
                bind(DownloadTokenCache.class).in(Singleton.class).to(DownloadTokenCache.class);
                bind(UiMasterProvider.class).in(Singleton.class).to(MasterProvider.class);
            }
        });
    }

}

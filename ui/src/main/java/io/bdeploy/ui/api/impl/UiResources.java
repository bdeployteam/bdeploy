package io.bdeploy.ui.api.impl;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.jersey.JerseyEagerServiceInitializer;
import io.bdeploy.jersey.RegistrationTarget;
import io.bdeploy.jersey.errorpages.JerseyCustomErrorPages;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.RemoteEntryStreamRequestService;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

public class UiResources {

    private UiResources() {
    }

    public static void register(RegistrationTarget server, boolean conCheckFailed) {

        if (conCheckFailed) {
            // we do not register the normal UI. instead, all requests will lead to an error page.
            server.addHandler(new HttpHandler() {

                @Override
                public void service(Request request, Response response) throws Exception {
                    String html = JerseyCustomErrorPages.getErrorHtml(503,
                            "<center>This server has encountered <b>internal connection issues</b>. Please check the <code>server.log</code> for more information."
                                    + "<br/>This problem is typically caused by a wrong hostname setting and/or network configuration issues."
                                    + "<br/>Despite the issues, the backend has started, and auto-start instances were processed.</center>");
                    response.setContentType(MediaType.TEXT_HTML);
                    response.setContentLength(html.length());
                    response.getWriter().write(html);
                }
            }, HttpHandlerRegistration.ROOT);
        } else {
            server.addHandler(new CLStaticHttpHandler(UiResources.class.getClassLoader(), "/webapp/"),
                    HttpHandlerRegistration.ROOT);
        }

        server.register(AuthResourceImpl.class);
        server.register(HiveResourceImpl.class);
        server.register(BackendInfoResourceImpl.class);
        server.register(NodeManagementResourceImpl.class);

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

        server.register(ProductValidationResourceImpl.class);

        // force immediate initialization of the manifest spawn bridge so it can attach to the hive registry.
        server.register(new JerseyEagerServiceInitializer<>(ManifestSpawnToChangeEventBridge.class));

        server.register(new AbstractBinder() {

            @Override
            protected void configure() {
                bind(DownloadTokenCache.class).in(Singleton.class).to(DownloadTokenCache.class);
                bind(ProductTransferService.class).in(Singleton.class).to(ProductTransferService.class);
                bind(RemoteEntryStreamRequestService.class).in(Singleton.class).to(RemoteEntryStreamRequestService.class);
                bind(ChangeEventManager.class).in(Singleton.class).to(ChangeEventManager.class);
                bind(ProductUpdateService.class).in(Singleton.class).to(ProductUpdateService.class);
                bind(ManifestSpawnToChangeEventBridge.class).in(Singleton.class).to(ManifestSpawnToChangeEventBridge.class);
                bind(RequestScopedParallelOperationsService.class).to(RequestScopedParallelOperationsService.class);
            }
        });
    }

    public static void registerNode(RegistrationTarget server) {
        server.addHandler(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                String html = JerseyCustomErrorPages.getErrorHtml(404,
                        "This server is a <code>NODE</code> and does not have a UI.");
                response.setContentType(MediaType.TEXT_HTML);
                response.setContentLength(html.length());
                response.getWriter().write(html);
            }
        }, HttpHandlerRegistration.ROOT);
    }

}

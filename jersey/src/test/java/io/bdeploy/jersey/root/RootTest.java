package io.bdeploy.jersey.root;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.ClientSslContextAccessor;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.UriBuilder;

class RootTest {

    @RegisterExtension
    private final TestServer ext = new TestServer();

    public RootTest() {
        // registers a handler which will server /fake-webapp/* from the current class loader,
        // which corresponds to the src/test/resources/fake-webapp folder in this project.
        ext.addHandler(new CLStaticHttpHandler(RootTest.class.getClassLoader(), "/fake-webapp/"), HttpHandlerRegistration.ROOT);
    }

    @Test
    void testRoot(RemoteService service, JerseyClientFactory f) {
        URI rootUri = UriBuilder.fromUri("https://localhost:" + service.getUri().getPort()).build();

        WebTarget root = ClientBuilder.newBuilder().sslContext(ClientSslContextAccessor.get(f)).hostnameVerifier((h, s) -> true)
                .build().target(rootUri);

        String content = root.request().get().readEntity(String.class);
        assertEquals("<h1>Fake!</h1>", content);
    }

}

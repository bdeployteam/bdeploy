package io.bdeploy.jersey.root;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.ClientSslContextAccessor;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;

public class RootTest {

    @RegisterExtension
    TestServer ext = new TestServer();

    public RootTest() {
        // registers a handler which will server /fake-webapp/* from the current class loader,
        // which corresponds to the src/test/resources/fake-webapp folder in this project.
        ext.registerRoot(new CLStaticHttpHandler(RootTest.class.getClassLoader(), "/fake-webapp/"));
    }

    @Test
    void root(RemoteService service, JerseyClientFactory f) {
        URI rootUri = UriBuilder.fromUri("https://localhost:" + service.getUri().getPort()).build();

        WebTarget root = ClientBuilder.newBuilder().sslContext(ClientSslContextAccessor.get(f)).hostnameVerifier((h, s) -> true)
                .build().target(rootUri);

        String content = root.request().get().readEntity(String.class);
        assertEquals("<h1>Fake!</h1>", content);
    }

}

package io.bdeploy.jersey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.ClientSslContextAccessor;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;

public class SecurityTest {

    @RegisterExtension
    TestServer ext = new TestServer(SecurityTestResourceImpl.class);

    @Test
    void testPlain(RemoteService service, JerseyClientFactory factory) {
        // manually construct without any additional filters, emulates "dumb" clients.
        // the only thing taken from the factory is the SSL context.
        WebTarget base = ClientBuilder.newBuilder().sslContext(ClientSslContextAccessor.get(factory))
                .hostnameVerifier((h, s) -> true).build().target(service.getUri()).path("/security");
        WebTarget unsecured = base.path("/unsecured");
        WebTarget secured = base.path("/secured");

        Response response = unsecured.request().get();

        assertEquals(200, response.getStatus());
        assertEquals("unsecured", response.readEntity(String.class));

        response = secured.request().get();

        assertEquals(response.getStatus(), 401);
    }

    @Test
    void testFactory(JerseyClientFactory factory) {
        SecurityTestResource rs = factory.getProxyClient(SecurityTestResource.class);

        assertEquals("unsecured", rs.testUnsecured());
        assertEquals("secured", rs.testSecured());
    }

}

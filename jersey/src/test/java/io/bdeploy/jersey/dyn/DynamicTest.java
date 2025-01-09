package io.bdeploy.jersey.dyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;
import jakarta.ws.rs.NotFoundException;

class DynamicTest {

    DynamicTestResourceLocatorImpl locator = new DynamicTestResourceLocatorImpl();

    @RegisterExtension
    private final TestServer ext = new TestServer(locator);

    @Test
    void testRegisterDynamic(JerseyClientFactory f) {
        locator.register("test", "value");

        DynamicTestResourceLocator svc = f.getProxyClient(DynamicTestResourceLocator.class);
        String value = svc.getNamedResource("test").getValue().str;

        assertEquals("value", value);
        assertThrows(NotFoundException.class, () -> {
            svc.getNamedResource("other").getValue();
        });

        locator.register("other", "more");
        assertEquals("more", svc.getNamedResource("other").getValue().str);
    }

}

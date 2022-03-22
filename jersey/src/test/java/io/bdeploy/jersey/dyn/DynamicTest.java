package io.bdeploy.jersey.dyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;

class DynamicTest {

    DynamicTestResourceLocatorImpl locator = new DynamicTestResourceLocatorImpl();

    @RegisterExtension
    TestServer ext = new TestServer(locator);

    @Test
    void registerDynamic(JerseyClientFactory f) {
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

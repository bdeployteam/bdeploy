package io.bdeploy.jersey.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.jersey.TestServer;

class InjectionTest {

    private static final String TEST = "test";

    @RegisterExtension
    TestServer ext = new TestServer(InjectionTestResourceImpl.class, new AbstractBinder() {

        @Override
        protected void configure() {
            bind(TEST).to(String.class).named(InjectionTestResource.INJECTED_STRING);
        }
    });

    @Test
    void injectSimpleString(InjectionTestResource rs) {
        assertEquals(TEST, rs.retrieveInjected());
    }

    @Test
    void injectProvidedString(InjectionTestResource rs) {
        assertEquals(TEST, rs.retrieveInjectedProvider());
    }

    @Test
    void injectUserPrincipal(InjectionTestResource rs) {
        // test tokens committed along with the tests are issued to the actual user.
        assertEquals(System.getProperty("user.name"), rs.retrieveUserFromToken());
    }

}

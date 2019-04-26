package io.bdeploy.jersey.dyn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.PathParam;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hk2.annotations.Service;

import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.dyn.DynamicTestResource.ValueDto;

public class DynamicInjectionTest {

    @RegisterExtension
    TestServer ext = new TestServer(new TestBinder(), DynamicInjectionTestResourceLocatorImpl.class);

    private class TestBinder extends AbstractBinder {

        @Override
        protected void configure() {
            bind(DynamicInjectionTestResourceImpl.class).in(RequestScoped.class).to(DynamicTestResource.class);
            bindFactory(TestFactory.class).in(RequestScoped.class).to(ValueDto.class);
            bindAsContract(TestRegistry.class).in(Singleton.class);
        }
    }

    @Service
    private static class TestRegistry {

        public Map<String, String> registry = new TreeMap<>();
    }

    private static class TestFactory implements Factory<ValueDto> {

        @PathParam("name")
        private Provider<String> param;

        @Inject
        private TestRegistry reg;

        @Override
        public ValueDto provide() {
            return new ValueDto(reg.registry.get("test"));
        }

        @Override
        public void dispose(ValueDto instance) {
        }

    }

    @Test
    void testInjection(DynamicInjectionTestResourceLocator locator, ServiceLocator hk2) {
        TestRegistry service = hk2.getService(TestRegistry.class);
        service.registry.put("test", "value");

        assertEquals("value", locator.getNamed("test").getValue().str);
    }

}

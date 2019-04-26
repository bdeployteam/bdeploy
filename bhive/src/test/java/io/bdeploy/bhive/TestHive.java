package io.bdeploy.bhive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Named;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;

public class TestHive implements ParameterResolver, BeforeEachCallback {

    private String name;

    public TestHive() {
        this(JerseyRemoteBHive.DEFAULT_NAME);
    }

    public TestHive(String name) {
        this.name = name;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        getExtensionStore(context).put("X-Hive-" + name, new CloseableTestHive());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (!parameterContext.getParameter().getType().isAssignableFrom(BHive.class)) {
            return false;
        }

        Named n = parameterContext.getParameter().getAnnotation(Named.class);
        String nm = n == null ? JerseyRemoteBHive.DEFAULT_NAME : n.value();
        return nm.equals(name);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return getExtensionStore(extensionContext).get("X-Hive-" + name, CloseableTestHive.class).hive;
    }

    private Store getExtensionStore(ExtensionContext context) {
        return context.getStore(Namespace.create(context.getRequiredTestMethod()));
    }

    private static class CloseableTestHive implements CloseableResource {

        private BHive hive;
        private Path path;

        public CloseableTestHive() throws IOException {
            path = Files.createTempDirectory("hive-");
            hive = new BHive(path.toUri(), new ActivityReporter.Null());
        }

        @Override
        public void close() throws Throwable {
            hive.close();
            PathHelper.deleteRecursive(path);
        }

    }

}

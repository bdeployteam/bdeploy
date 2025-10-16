package io.bdeploy.bhive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import jakarta.inject.Named;

public class TestHive implements ParameterResolver, BeforeEachCallback {

    private final String name;

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

    private static Store getExtensionStore(ExtensionContext context) {
        return context.getStore(Namespace.create(context.getRequiredTestMethod()));
    }

    private static class CloseableTestHive implements AutoCloseable {

        private final BHive hive;
        private final Path path;

        public CloseableTestHive() throws IOException {
            path = Files.createTempDirectory("hive-");
            hive = new BHive(path.toUri(), new TestAuditor(), new ActivityReporter.Null());
        }

        @Override
        public void close() {
            hive.close();
            PathHelper.deleteRecursiveRetry(path);
        }

    }

}
